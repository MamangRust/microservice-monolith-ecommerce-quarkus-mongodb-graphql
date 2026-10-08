package com.sanedge.order.seed;

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Seeds the sales context: orders, order_items, and shipping_addresses.
 * Order = 30. Uses fixed createdAt dates so stats are deterministic.
 *
 * <p>Idempotent: upserts on the synthetic {@code _seedKey} field.</p>
 */
public class OrderSeeder implements Seeder {

    private static final String[][] ORDERS = {
            {"1", "1", "15000000", "2024-01-15 10:30:00"},
            {"1", "1", "700000", "2024-01-20 14:00:00"},
            {"1", "2", "500000", "2024-02-05 09:00:00"},
            {"2", "1", "8000000", "2024-02-10 11:30:00"},
            {"2", "3", "2500000", "2024-03-01 16:00:00"},
            {"3", "1", "450000", "2024-03-15 13:00:00"},
            {"3", "2", "1200000", "2024-04-01 10:00:00"},
            {"1", "4", "350000", "2024-04-20 15:30:00"},
            {"2", "5", "250000", "2024-05-01 08:00:00"},
            {"3", "1", "15000000", "2024-05-15 12:00:00"}
    };

    private static final String[] PROVINCES = {"DKI Jakarta", "Jawa Barat", "Jawa Timur", "Bali", "Sumatera Utara"};
    private static final String[] CITIES = {"Jakarta Pusat", "Bandung", "Surabaya", "Denpasar", "Medan"};

    @Override
    public String domain() {
        return "order";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();
        ReactiveMongoDatabase sales = ctx.db("sales");
        ReactiveMongoDatabase catalog = ctx.db("catalog");
        ReactiveMongoDatabase identity = ctx.db("identity");
        ReactiveMongoDatabase merchant = ctx.db("merchant");

        ReactiveMongoCollection<Document> orders = sales.getCollection("orders", Document.class);
        ReactiveMongoCollection<Document> orderItems = sales.getCollection("order_items", Document.class);
        ReactiveMongoCollection<Document> shipping = sales.getCollection("shipping_addresses", Document.class);
        ReactiveMongoCollection<Document> products = catalog.getCollection("products", Document.class);
        ReactiveMongoCollection<Document> users = identity.getCollection("users", Document.class);
        ReactiveMongoCollection<Document> merchants = merchant.getCollection("merchants", Document.class);

        return users.find(new Document()).collect().asList()
                .chain(userDocs -> merchants.find(new Document()).collect().asList()
                        .chain(merchantDocs -> products.find(new Document()).collect().asList()
                                .chain(productDocs -> seedAll(orders, orderItems, shipping, userDocs, merchantDocs,
                                        productDocs, log))));
    }

    private Uni<Void> seedAll(ReactiveMongoCollection<Document> orders,
                              ReactiveMongoCollection<Document> orderItems,
                              ReactiveMongoCollection<Document> shipping,
                              List<Document> userDocs,
                              List<Document> merchantDocs,
                              List<Document> productDocs,
                              Logger log) {
        List<ObjectId> userIds = userDocs.stream().map(d -> d.getObjectId("_id")).toList();
        List<ObjectId> merchantIds = merchantDocs.stream().map(d -> d.getObjectId("_id")).toList();

        List<ObjectId> orderIds = new ArrayList<>();
        Uni<Void> chain = Uni.createFrom().voidItem();

        for (int i = 0; i < ORDERS.length; i++) {
            String[] o = ORDERS[i];
            ObjectId orderId = new ObjectId();
            orderIds.add(orderId);
            ObjectId userId = pick(userIds, Integer.parseInt(o[0]) - 1);
            ObjectId merchantId = pick(merchantIds, Integer.parseInt(o[1]) - 1);
            Date createdAt = Date.from(LocalDateTime.parse(o[3].replace(' ', 'T')).toInstant(ZoneOffset.UTC));
            String seedKey = "order-" + i;
            chain = chain.chain(() -> orders.updateOne(
                    Filters.eq("_seedKey", seedKey),
                    Updates.combine(
                            Updates.setOnInsert("_seedKey", seedKey),
                            Updates.setOnInsert("userId", userId),
                            Updates.setOnInsert("merchantId", merchantId),
                            Updates.setOnInsert("totalPrice", Integer.parseInt(o[2])),
                            Updates.setOnInsert("createdAt", createdAt),
                            Updates.setOnInsert("updatedAt", createdAt),
                            Updates.setOnInsert("deletedAt", null)),
                    new UpdateOptions().upsert(true)).replaceWithVoid());
        }

        chain = chain.invoke(() -> log.infof("Orders seeded: %d orders", ORDERS.length));

        // order_items: one per order, product/price taken from the catalog
        int itemCount = 0;
        for (int i = 0; i < orderIds.size() && !productDocs.isEmpty(); i++) {
            ObjectId orderId = orderIds.get(i);
            Document product = productDocs.get(i % productDocs.size());
            ObjectId productId = product.getObjectId("_id");
            int price = product.getInteger("price") == null ? 0 : product.getInteger("price");
            int quantity = 1 + (i % 3);
            Date createdAt = Date.from(LocalDateTime.parse(ORDERS[Math.min(i, ORDERS.length - 1)][3].replace(' ', 'T'))
                    .toInstant(ZoneOffset.UTC));
            String seedKey = "order-item-" + i;
            itemCount++;
            chain = chain.chain(() -> orderItems.updateOne(
                    Filters.eq("_seedKey", seedKey),
                    Updates.combine(
                            Updates.setOnInsert("_seedKey", seedKey),
                            Updates.setOnInsert("orderId", orderId),
                            Updates.setOnInsert("productId", productId),
                            Updates.setOnInsert("quantity", quantity),
                            Updates.setOnInsert("price", price * quantity),
                            Updates.setOnInsert("createdAt", createdAt),
                            Updates.setOnInsert("updatedAt", createdAt),
                            Updates.setOnInsert("deletedAt", null)),
                    new UpdateOptions().upsert(true)).replaceWithVoid());
        }
        final int seededItems = itemCount;
        chain = chain.invoke(() -> log.infof("Order items seeded: %d items", seededItems));

        // shipping_addresses: one per order
        int shipCount = 0;
        for (int i = 0; i < orderIds.size(); i++) {
            ObjectId orderId = orderIds.get(i);
            int p = i % PROVINCES.length;
            Date now = Date.from(Instant.now());
            String seedKey = "shipping-" + i;
            shipCount++;
            chain = chain.chain(() -> shipping.updateOne(
                    Filters.eq("_seedKey", seedKey),
                    Updates.combine(
                            Updates.setOnInsert("_seedKey", seedKey),
                            Updates.setOnInsert("orderId", orderId),
                            Updates.setOnInsert("alamat", "Jl. Sudirman No. 1"),
                            Updates.setOnInsert("provinsi", PROVINCES[p]),
                            Updates.setOnInsert("negara", "Indonesia"),
                            Updates.setOnInsert("kota", CITIES[p]),
                            Updates.setOnInsert("courier", "JNE"),
                            Updates.setOnInsert("shippingMethod", "Regular"),
                            Updates.setOnInsert("shippingCost", 15000 + (p * 5000)),
                            Updates.setOnInsert("createdAt", now),
                            Updates.setOnInsert("updatedAt", now),
                            Updates.setOnInsert("deletedAt", null)),
                    new UpdateOptions().upsert(true)).replaceWithVoid());
        }
        final int seededShipping = shipCount;

        return chain.invoke(() -> log.infof("Shipping addresses seeded: %d", seededShipping));
    }

    private static ObjectId pick(List<ObjectId> ids, int index) {
        if (ids.isEmpty()) {
            return new ObjectId();
        }
        return ids.get(Math.floorMod(index, ids.size()));
    }
}
