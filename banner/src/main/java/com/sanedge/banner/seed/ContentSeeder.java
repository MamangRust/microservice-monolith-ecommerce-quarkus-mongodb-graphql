package com.sanedge.banner.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * sliders and banners. Order = 50.
 *
 * <p>Writes idempotently (check-then-insert on the natural key {@code name}) into
 * the {@code sliders} and {@code banners} collections of the experience context.
 */
public class ContentSeeder implements Seeder {

    private static final String[][] SLIDERS = {
            { "Summer Sale", "slider-summer.png" },
            { "New Arrivals", "slider-new.png" },
            { "Free Shipping", "slider-shipping.png" },
            { "Flash Deal", "slider-flash.png" },
            { "Clearance", "slider-clearance.png" }
    };

    private static final String[][] BANNERS = {
            { "Electronics Week", "2024-01-01", "2024-01-31", "00:00:00", "23:59:59", "true" },
            { "Fashion Sale", "2024-02-01", "2024-02-28", "00:00:00", "23:59:59", "true" },
            { "Home Living Promo", "2024-03-01", "2024-03-31", "00:00:00", "23:59:59", "true" },
            { "Beauty Festival", "2024-04-01", "2024-04-30", "00:00:00", "23:59:59", "true" },
            { "Sports Month", "2024-05-01", "2024-05-31", "00:00:00", "23:59:59", "false" }
    };

    @Override
    public String domain() {
        return "content";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();

        ReactiveMongoCollection<Document> sliders = ctx.db("experience").getCollection("sliders");
        ReactiveMongoCollection<Document> banners = ctx.db("experience").getCollection("banners");

        return seedSliders(sliders, log)
                .chain(() -> seedBanners(banners, log))
                .replaceWithVoid();
    }

    private Uni<Void> seedSliders(ReactiveMongoCollection<Document> sliders, Logger log) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String[] s : SLIDERS) {
            chain = chain.chain(() -> insertIfAbsent(sliders, new Document("name", s[0]),
                    new Document("name", s[0]).append("image", s[1])));
        }
        return chain.invoke(() -> log.infof("Sliders seeded: %d sliders", SLIDERS.length));
    }

    private Uni<Void> seedBanners(ReactiveMongoCollection<Document> banners, Logger log) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String[] b : BANNERS) {
            Document fields = new Document("name", b[0])
                    .append("startDate", LocalDate.parse(b[1]))
                    .append("endDate", LocalDate.parse(b[2]))
                    .append("startTime", LocalTime.parse(b[3]))
                    .append("endTime", LocalTime.parse(b[4]))
                    .append("isActive", Boolean.parseBoolean(b[5]));
            chain = chain.chain(() -> insertIfAbsent(banners, new Document("name", b[0]), fields));
        }
        return chain.invoke(() -> log.infof("Banners seeded: %d banners", BANNERS.length));
    }

    /**
     * Idempotent insert: skips when a document already exists for the given
     * filter, otherwise inserts {@code fields} with audit timestamps.
     */
    private Uni<Void> insertIfAbsent(ReactiveMongoCollection<Document> collection, Document filter,
            Document fields) {
        return firstOrNull(collection, filter)
                .chain(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().voidItem();
                    }
                    Instant now = Instant.now();
                    fields.append("_id", new ObjectId())
                            .append("createdAt", now)
                            .append("updatedAt", now)
                            .append("deletedAt", null);
                    return collection.insertOne(fields).replaceWithVoid();
                });
    }

    private Uni<Document> firstOrNull(ReactiveMongoCollection<Document> collection, Document filter) {
        return collection.find(filter).collect().asList()
                .map((List<Document> docs) -> docs.isEmpty() ? null : docs.get(0));
    }
}
