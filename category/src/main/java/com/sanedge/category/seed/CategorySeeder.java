package com.sanedge.category.seed;

import com.sanedge.common.seed.SeedContext;
import com.sanedge.common.seed.Seeder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Uni;
import org.bson.Document;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Seeds the catalog context: 10 categories.
 * Order = 20.
 *
 * <p>Writes directly to the {@code categories} collection of the catalog
 * MongoDB instance via {@link SeedContext#db(String)}. Idempotent: upserts on
 * the natural key {@code slugCategory} so re-running never duplicates rows.</p>
 */
public class CategorySeeder implements Seeder {

    private static final List<String[]> CATEGORIES = List.of(
            new String[]{"Electronics", "Electronic devices and gadgets", "electronics"},
            new String[]{"Fashion", "Clothing, shoes, and accessories", "fashion"},
            new String[]{"Home & Living", "Furniture and home decor", "home-living"},
            new String[]{"Beauty", "Skincare, makeup, and beauty products", "beauty"},
            new String[]{"Sports", "Sports equipment and outdoor gear", "sports"},
            new String[]{"Books", "Books, magazines, and educational materials", "books"},
            new String[]{"Food & Beverage", "Food, snacks, and beverages", "food-beverage"},
            new String[]{"Automotive", "Automotive parts and accessories", "automotive"},
            new String[]{"Health", "Health supplements and wellness products", "health"},
            new String[]{"Toys", "Toys, games, and entertainment", "toys"}
    );

    @Override
    public String domain() {
        return "category";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();
        ReactiveMongoDatabase database = ctx.db("catalog");
        ReactiveMongoCollection<Document> collection = database.getCollection("categories", Document.class);

        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String[] cat : CATEGORIES) {
            String name = cat[0];
            String description = cat[1];
            String slug = cat[2];
            chain = chain.chain(() -> {
                Date now = Date.from(Instant.now());
                return collection.updateOne(
                        Filters.eq("slugCategory", slug),
                        Updates.combine(
                                Updates.setOnInsert("name", name),
                                Updates.setOnInsert("description", description),
                                Updates.setOnInsert("slugCategory", slug),
                                Updates.setOnInsert("createdAt", now),
                                Updates.setOnInsert("updatedAt", now)),
                        new UpdateOptions().upsert(true))
                        .replaceWithVoid();
            });
        }

        return chain.invoke(() -> log.infof("Categories seeded: %d categories", CATEGORIES.size()));
    }
}
