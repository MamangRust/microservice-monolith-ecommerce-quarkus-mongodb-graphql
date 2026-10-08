package com.sanedge.product.seed;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the catalog context: 20 products.
 * Order = 30.
 *
 * <p>Writes directly to the {@code products} collection of the catalog MongoDB
 * instance. The first merchant from the merchant context is used as the owner
 * of every seeded product (cross-context reference by ObjectId). Idempotent:
 * upserts on the natural key {@code slugProduct}.</p>
 */
public class ProductSeeder implements Seeder {

    private static final String[][] PRODUCTS = {
            {"Laptop Pro 15", "electronics", "15000000", "50", "TechBrand", "2000", "laptop-pro-15"},
            {"Wireless Mouse", "electronics", "250000", "200", "LogiMax", "200", "wireless-mouse"},
            {"USB-C Hub", "electronics", "450000", "150", "TechBrand", "300", "usb-c-hub"},
            {"Smartphone X", "electronics", "8000000", "75", "PhoneCo", "180", "smartphone-x"},
            {"T-Shirt Basic", "fashion", "150000", "300", "FashionCo", "200", "t-shirt-basic"},
            {"Denim Jacket", "fashion", "500000", "80", "FashionCo", "800", "denim-jacket"},
            {"Running Shoes", "sports", "1200000", "120", "SportMax", "600", "running-shoes"},
            {"Yoga Mat", "sports", "350000", "200", "FitLife", "1500", "yoga-mat"},
            {"Coffee Table", "home-living", "2500000", "30", "HomeStyle", "15000", "coffee-table"},
            {"Desk Lamp", "home-living", "450000", "100", "LightMax", "800", "desk-lamp"},
            {"Face Cream", "beauty", "250000", "250", "GlowUp", "100", "face-cream"},
            {"Lipstick Set", "beauty", "350000", "180", "GlowUp", "50", "lipstick-set"},
            {"Programming Book", "books", "180000", "100", "TechPress", "500", "programming-book"},
            {"Recipe Book", "books", "120000", "80", "FoodPress", "400", "recipe-book"},
            {"Snack Box", "food-beverage", "85000", "500", "TastyCo", "300", "snack-box"},
            {"Coffee Beans", "food-beverage", "150000", "300", "BrewMax", "500", "coffee-beans"},
            {"Car Phone Mount", "automotive", "120000", "150", "AutoGear", "200", "car-phone-mount"},
            {"Vitamin C", "health", "95000", "400", "VitaLife", "100", "vitamin-c"},
            {"Building Blocks", "toys", "250000", "120", "PlayMax", "800", "building-blocks"},
            {"Remote Control Car", "toys", "450000", "60", "PlayMax", "500", "rc-car"}
    };

    @Override
    public String domain() {
        return "product";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();
        ReactiveMongoDatabase catalogDb = ctx.db("catalog");
        ReactiveMongoDatabase merchantDb = ctx.db("merchant");
        ReactiveMongoCollection<Document> products = catalogDb.getCollection("products", Document.class);
        ReactiveMongoCollection<Document> categories = catalogDb.getCollection("categories", Document.class);
        ReactiveMongoCollection<Document> merchants = merchantDb.getCollection("merchants", Document.class);

        return merchants.find(new Document()).collect().first()
                .onItem().ifNull().continueWith(() -> new Document("_id", new ObjectId()))
                .chain(merchantDoc -> {
                    ObjectId merchantId = merchantDoc.getObjectId("_id");
                    return categories.find(new Document()).collect().asList()
                            .chain(categoryDocs -> {
                                Map<String, ObjectId> categoryMap = new HashMap<>();
                                for (Document c : categoryDocs) {
                                    categoryMap.put(c.getString("slugCategory"), c.getObjectId("_id"));
                                }
                                return seedProductsSequential(products, merchantId, categoryMap, log);
                            });
                })
                .replaceWithVoid();
    }

    private Uni<Void> seedProductsSequential(ReactiveMongoCollection<Document> products,
                                             ObjectId merchantId,
                                             Map<String, ObjectId> categoryMap,
                                             Logger log) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String[] p : PRODUCTS) {
            String name = p[0];
            ObjectId categoryId = categoryMap.getOrDefault(p[1], new ObjectId());
            int price = Integer.parseInt(p[2]);
            int countInStock = Integer.parseInt(p[3]);
            String brand = p[4];
            int weight = Integer.parseInt(p[5]);
            String slug = p[6];

            chain = chain.chain(() -> {
                Date now = Date.from(Instant.now());
                return products.updateOne(
                        Filters.eq("slugProduct", slug),
                        Updates.combine(
                                Updates.setOnInsert("merchantId", merchantId),
                                Updates.setOnInsert("categoryId", categoryId),
                                Updates.setOnInsert("name", name),
                                Updates.setOnInsert("description", "Description for " + name),
                                Updates.setOnInsert("price", price),
                                Updates.setOnInsert("countInStock", countInStock),
                                Updates.setOnInsert("brand", brand),
                                Updates.setOnInsert("weight", weight),
                                Updates.setOnInsert("rating", 4.5f),
                                Updates.setOnInsert("slugProduct", slug),
                                Updates.setOnInsert("imageProduct", null),
                                Updates.setOnInsert("createdAt", now),
                                Updates.setOnInsert("updatedAt", now)),
                        new UpdateOptions().upsert(true))
                        .replaceWithVoid();
            });
        }

        return chain.invoke(() -> log.infof("Products seeded: %d products", PRODUCTS.length));
    }
}
