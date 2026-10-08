package com.sanedge.transaction.seed;

import com.sanedge.common.seed.SeedContext;
import com.sanedge.common.seed.Seeder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Uni;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Seeds the sales context: transactions (one per order).
 * Order = 40. Idempotent: upserts on the synthetic {@code _seedKey} field.
 */
public class TransactionSeeder implements Seeder {

    private static final String[] PAYMENT_METHODS = {"Bank Transfer", "Credit Card", "E-Wallet", "COD"};

    @Override
    public String domain() {
        return "transaction";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();
        ReactiveMongoDatabase sales = ctx.db("sales");
        ReactiveMongoCollection<Document> orders = sales.getCollection("orders", Document.class);
        ReactiveMongoCollection<Document> transactions = sales.getCollection("transactions", Document.class);

        return orders.find(new Document()).collect().asList()
                .chain(orderDocs -> {
                    Uni<Void> chain = Uni.createFrom().voidItem();
                    int idx = 0;
                    for (Document order : orderDocs) {
                        ObjectId orderId = order.getObjectId("_id");
                        ObjectId merchantId = order.getObjectId("merchantId");
                        int amount = order.getInteger("totalPrice") == null ? 0 : order.getInteger("totalPrice");
                        String method = PAYMENT_METHODS[idx % PAYMENT_METHODS.length];
                        String status = idx % 5 == 0 ? "FAILED" : "SUCCESS";
                        Date createdAt = order.getDate("createdAt") == null ? Date.from(Instant.now())
                                : order.getDate("createdAt");
                        String seedKey = "transaction-" + idx;
                        idx++;
                        chain = chain.chain(() -> transactions.updateOne(
                                Filters.eq("_seedKey", seedKey),
                                Updates.combine(
                                        Updates.setOnInsert("_seedKey", seedKey),
                                        Updates.setOnInsert("orderId", orderId),
                                        Updates.setOnInsert("merchantId", merchantId),
                                        Updates.setOnInsert("paymentMethod", method),
                                        Updates.setOnInsert("amount", amount),
                                        Updates.setOnInsert("status", status),
                                        Updates.setOnInsert("createdAt", createdAt),
                                        Updates.setOnInsert("updatedAt", createdAt),
                                        Updates.setOnInsert("deletedAt", null)),
                                new UpdateOptions().upsert(true)).replaceWithVoid());
                    }
                    return chain;
                })
                .invoke(() -> log.info("Transactions seeded"));
    }
}
