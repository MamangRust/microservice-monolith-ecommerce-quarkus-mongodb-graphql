package com.sanedge.review.seed;

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
 * reviews and review_details. Order = 50.
 *
 * <p>Reads the first users from the identity context ({@code ec_identity.users})
 * and the first products from the catalog context ({@code ec_catalog.products}),
 * then writes reviews idempotently (check-then-insert on
 * {@code userId + productId + comment}) followed by review_details.
 */
public class ReviewSeeder implements Seeder {

    private static final String[][] REVIEWS = {
            { "1", "1", "Admin", "Great laptop, fast performance!", "5" },
            { "2", "1", "Staff", "Good value for money", "4" },
            { "3", "2", "User", "Nice mouse, comfortable to use", "4" },
            { "1", "3", "Admin", "Useful hub, all ports work", "5" },
            { "2", "4", "Staff", "Excellent phone, great camera", "5" },
            { "3", "5", "User", "Basic but comfortable shirt", "3" },
            { "1", "7", "Admin", "Perfect running shoes", "5" },
            { "2", "8", "Staff", "Good yoga mat, non-slip", "4" },
            { "3", "9", "User", "Sturdy table, easy assembly", "4" },
            { "1", "11", "Admin", "Great face cream, skin feels soft", "5" }
    };

    @Override
    public String domain() {
        return "review";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();

        ReactiveMongoCollection<Document> users = ctx.db("identity").getCollection("users");
        ReactiveMongoCollection<Document> products = ctx.db("catalog").getCollection("products");
        ReactiveMongoCollection<Document> reviews = ctx.db("experience").getCollection("reviews");
        ReactiveMongoCollection<Document> details = ctx.db("experience").getCollection("review_details");

        return users.find().collect().asList()
                .map(docs -> limit(docs, 3))
                .chain(userDocs -> products.find().collect().asList()
                        .map(docs -> limit(docs, 10))
                        .chain(productDocs -> seedReviewsSequential(reviews, userDocs, productDocs, log)))
                .chain(reviewIds -> seedReviewDetails(details, reviewIds, log))
                .replaceWithVoid();
    }

    private List<Document> limit(List<Document> docs, int max) {
        return new ArrayList<>(docs.subList(0, Math.min(max, docs.size())));
    }

    private Uni<List<ObjectId>> seedReviewsSequential(ReactiveMongoCollection<Document> reviews,
            List<Document> users, List<Document> products, Logger log) {
        if (users.isEmpty() || products.isEmpty()) {
            return Uni.createFrom().<List<ObjectId>>item(new ArrayList<>())
                    .invoke(ids -> log.warn("Reviews seeding skipped: no users or products available"));
        }

        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (String[] r : REVIEWS) {
            ObjectId userId = users.get((Integer.parseInt(r[0]) - 1) % users.size()).getObjectId("_id");
            ObjectId productId = products.get((Integer.parseInt(r[1]) - 1) % products.size()).getObjectId("_id");

            chain = chain.chain(ids -> ensureReview(reviews, userId, productId, r[2], r[3], Integer.parseInt(r[4]))
                    .map(id -> {
                        ids.add(id);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Reviews seeded: %d reviews", ids.size()));
    }

    private Uni<ObjectId> ensureReview(ReactiveMongoCollection<Document> reviews, ObjectId userId,
            ObjectId productId, String name, String comment, int rating) {
        Document filter = new Document("userId", userId)
                .append("productId", productId)
                .append("comment", comment);

        return firstOrNull(reviews, filter)
                .chain(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().item(existing.getObjectId("_id"));
                    }
                    ObjectId id = new ObjectId();
                    Instant now = Instant.now();
                    Document doc = new Document("_id", id)
                            .append("userId", userId)
                            .append("productId", productId)
                            .append("name", name)
                            .append("comment", comment)
                            .append("rating", rating)
                            .append("createdAt", now)
                            .append("updatedAt", now)
                            .append("deletedAt", null);
                    return reviews.insertOne(doc).map(v -> id);
                });
    }

    private Uni<Void> seedReviewDetails(ReactiveMongoCollection<Document> details,
            List<ObjectId> reviewIds, Logger log) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int i = 0; i < Math.min(5, reviewIds.size()); i++) {
            ObjectId reviewId = reviewIds.get(i);
            String caption = "Photo " + (i + 1);
            String url = "https://example.com/review/" + reviewId.toHexString() + ".jpg";
            Document filter = new Document("reviewId", reviewId).append("url", url);
            Document fields = new Document("reviewId", reviewId)
                    .append("type", "photo")
                    .append("url", url)
                    .append("caption", caption);
            chain = chain.chain(() -> insertIfAbsent(details, filter, fields));
        }
        return chain.invoke(() -> log.infof("Review details seeded: %d", Math.min(5, reviewIds.size())));
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
