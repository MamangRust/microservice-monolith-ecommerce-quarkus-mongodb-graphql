package com.sanedge.cart.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import com.sanedge.common.seed.SeedContext;
import com.sanedge.common.seed.Seeder;

import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;

/**
 * Seeds the experience bounded context (MongoDB database {@code ec_experience}):
 * carts. Order = 30.
 *
 * <p>Reads the first users from the identity context ({@code ec_identity.users})
 * and the first products from the catalog context ({@code ec_catalog.products}),
 * then writes carts idempotently (check-then-insert on {@code userId + productId})
 * into the experience context.
 */
public class CartSeeder implements Seeder {

    @Override
    public String domain() {
        return "cart";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();

        ReactiveMongoCollection<Document> users = ctx.db("identity").getCollection("users");
        ReactiveMongoCollection<Document> products = ctx.db("catalog").getCollection("products");
        ReactiveMongoCollection<Document> carts = ctx.db("experience").getCollection("carts");

        return users.find().collect().asList()
                .map(docs -> limit(docs, 3))
                .chain(userDocs -> products.find().collect().asList()
                        .map(docs -> limit(docs, 5))
                        .chain(productDocs -> seedCartsSequential(carts, userDocs, productDocs, log)))
                .replaceWithVoid();
    }

    private List<Document> limit(List<Document> docs, int max) {
        return new ArrayList<>(docs.subList(0, Math.min(max, docs.size())));
    }

    private Uni<Void> seedCartsSequential(ReactiveMongoCollection<Document> carts,
            List<Document> users, List<Document> products, Logger log) {
        if (users.isEmpty() || products.isEmpty()) {
            return Uni.createFrom().voidItem()
                    .invoke(() -> log.warn("Carts seeding skipped: no users or products available"));
        }

        Uni<Void> chain = Uni.createFrom().voidItem();
        int cartIdx = 0;
        for (Document user : users) {
            ObjectId userId = user.getObjectId("_id");
            for (int pIdx = 0; pIdx < Math.min(2, products.size()); pIdx++) {
                Document product = products.get((cartIdx + pIdx) % products.size());
                int qty = 1 + (cartIdx % 3);
                chain = chain.chain(() -> ensureCart(carts, userId, product, qty));
                cartIdx++;
            }
        }

        return chain.invoke(() -> log.info("Carts seeded"));
    }

    private Uni<Void> ensureCart(ReactiveMongoCollection<Document> carts, ObjectId userId,
            Document product, int quantity) {
        ObjectId productId = product.getObjectId("_id");
        Document filter = new Document("userId", userId).append("productId", productId);

        return carts.find(filter).collect().asList()
                .map(docs -> docs.isEmpty() ? null : docs.get(0))
                .chain(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().voidItem();
                    }
                    Instant now = Instant.now();
                    String image = product.getString("imageProduct");
                    Document doc = new Document("_id", new ObjectId())
                            .append("userId", userId)
                            .append("productId", productId)
                            .append("name", product.getString("name"))
                            .append("price", product.getInteger("price"))
                            .append("image", image != null ? image : "default.png")
                            .append("quantity", quantity)
                            .append("weight", 500)
                            .append("createdAt", now)
                            .append("updatedAt", now)
                            .append("deletedAt", null);
                    return carts.insertOne(doc).replaceWithVoid();
                });
    }
}
