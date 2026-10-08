package com.sanedge.merchant.seed;

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
 * Seeds the merchant bounded context (MongoDB database {@code ec_merchant}):
 * merchants and sub-entities. Order = 20.
 *
 * <p>Reads the first users from the identity context ({@code ec_identity.users})
 * to reference as merchant owners, then writes idempotently (check-then-insert on
 * the natural key) into the merchant context collections.
 */
public class MerchantSeeder implements Seeder {

    private static final String[][] MERCHANTS = {
            { "Tech Store", "Best electronics store", "Jakarta", "tech@example.com", "081234567890", "APPROVED" },
            { "Fashion Hub", "Trendy fashion outlet", "Bandung", "fashion@example.com", "081234567891", "APPROVED" },
            { "Home Decor", "Quality home furniture", "Surabaya", "home@example.com", "081234567892", "APPROVED" },
            { "Beauty World", "Premium beauty products", "Yogyakarta", "beauty@example.com", "081234567893", "APPROVED" },
            { "Sports Zone", "Sports equipment specialist", "Semarang", "sports@example.com", "081234567894", "APPROVED" }
    };

    @Override
    public String domain() {
        return "merchant";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();

        ReactiveMongoCollection<Document> users = ctx.db("identity").getCollection("users");
        ReactiveMongoCollection<Document> merchants = ctx.db("merchant").getCollection("merchants");
        ReactiveMongoCollection<Document> details = ctx.db("merchant").getCollection("merchant_details");
        ReactiveMongoCollection<Document> awards = ctx.db("merchant")
                .getCollection("merchant_certifications_and_awards");
        ReactiveMongoCollection<Document> policies = ctx.db("merchant").getCollection("merchant_policies");
        ReactiveMongoCollection<Document> business = ctx.db("merchant")
                .getCollection("merchant_business_information");

        return users.find().collect().asList()
                .map(this::extractUserIds)
                .map(this::limitToMerchants)
                .chain(userIds -> seedMerchantsSequential(merchants, userIds, log))
                .chain(merchantIds -> seedMerchantDetails(details, merchantIds, log))
                .chain(merchantIds -> seedAwards(awards, merchantIds, log))
                .chain(merchantIds -> seedPolicies(policies, merchantIds, log))
                .chain(merchantIds -> seedBusinessInfo(business, merchantIds, log))
                .replaceWithVoid();
    }

    private List<ObjectId> extractUserIds(List<Document> users) {
        List<ObjectId> ids = new ArrayList<>();
        for (Document user : users) {
            ObjectId id = user.getObjectId("_id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private List<ObjectId> limitToMerchants(List<ObjectId> userIds) {
        List<ObjectId> limited = new ArrayList<>();
        for (int i = 0; i < Math.min(userIds.size(), MERCHANTS.length); i++) {
            ObjectId userId = userIds.get(i);
            if (userId != null) {
                limited.add(userId);
            }
        }
        return limited;
    }

    private Uni<List<ObjectId>> seedMerchantsSequential(ReactiveMongoCollection<Document> merchants,
            List<ObjectId> userIds, Logger log) {
        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (int i = 0; i < userIds.size(); i++) {
            ObjectId userId = userIds.get(i);
            String[] m = MERCHANTS[i];
            chain = chain.chain(ids -> ensureMerchant(merchants, userId, m)
                    .map(id -> {
                        ids.add(id);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Merchants seeded: %d merchants", ids.size()));
    }

    private Uni<ObjectId> ensureMerchant(ReactiveMongoCollection<Document> merchants, ObjectId userId, String[] m) {
        return merchants.find(new Document("name", m[0])).collect().asList()
                .map(list -> list.isEmpty() ? null : list.get(0))
                .chain(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().item(existing.getObjectId("_id"));
                    }
                    ObjectId id = new ObjectId();
                    Instant now = Instant.now();
                    Document doc = new Document("_id", id)
                            .append("userId", userId)
                            .append("name", m[0])
                            .append("description", m[1])
                            .append("address", m[2])
                            .append("contactEmail", m[3])
                            .append("contactPhone", m[4])
                            .append("status", m[5])
                            .append("createdAt", now)
                            .append("updatedAt", now)
                            .append("deletedAt", null);
                    return merchants.insertOne(doc).map(v -> id);
                });
    }

    private Uni<List<ObjectId>> seedMerchantDetails(ReactiveMongoCollection<Document> details,
            List<ObjectId> merchantIds, Logger log) {
        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (ObjectId merchantId : merchantIds) {
            String hex = merchantId.toHexString();
            chain = chain.chain(ids -> insertIfAbsent(details, merchantId,
                    new Document("merchantId", merchantId)
                            .append("displayName", "Store #" + hex)
                            .append("shortDescription", "Description for merchant " + hex))
                    .map(v -> {
                        ids.add(merchantId);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Merchant details seeded: %d", ids.size()));
    }

    private Uni<List<ObjectId>> seedAwards(ReactiveMongoCollection<Document> awards,
            List<ObjectId> merchantIds, Logger log) {
        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (ObjectId merchantId : merchantIds) {
            chain = chain.chain(ids -> insertIfAbsent(awards, merchantId,
                    new Document("merchantId", merchantId)
                            .append("title", "Best Seller " + merchantId.toHexString())
                            .append("description", "Award for outstanding sales"))
                    .map(v -> {
                        ids.add(merchantId);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Merchant awards seeded: %d", ids.size()));
    }

    private Uni<List<ObjectId>> seedPolicies(ReactiveMongoCollection<Document> policies,
            List<ObjectId> merchantIds, Logger log) {
        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (ObjectId merchantId : merchantIds) {
            chain = chain.chain(ids -> insertIfAbsent(policies, merchantId,
                    new Document("merchantId", merchantId)
                            .append("policyType", "Return")
                            .append("title", "Return Policy")
                            .append("description", "30-day return policy for all products"))
                    .map(v -> {
                        ids.add(merchantId);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Merchant policies seeded: %d", ids.size()));
    }

    private Uni<List<ObjectId>> seedBusinessInfo(ReactiveMongoCollection<Document> business,
            List<ObjectId> merchantIds, Logger log) {
        Uni<List<ObjectId>> chain = Uni.createFrom().item(new ArrayList<ObjectId>());

        for (int idx = 0; idx < merchantIds.size(); idx++) {
            ObjectId merchantId = merchantIds.get(idx);
            int year = 2020 + idx;
            String taxId = "1234567890" + idx;
            chain = chain.chain(ids -> insertIfAbsent(business, merchantId,
                    new Document("merchantId", merchantId)
                            .append("businessType", "PT")
                            .append("taxId", taxId)
                            .append("establishedYear", year))
                    .map(v -> {
                        ids.add(merchantId);
                        return ids;
                    }));
        }

        return chain.invoke(ids -> log.infof("Merchant business info seeded: %d", ids.size()));
    }

    /**
     * Idempotent insert: skips when a document already exists for the given
     * merchant, otherwise inserts {@code fields} with audit timestamps.
     */
    private Uni<Void> insertIfAbsent(ReactiveMongoCollection<Document> collection, ObjectId merchantId,
            Document fields) {
        return collection.find(new Document("merchantId", merchantId)).collect().asList()
                .map(list -> list.isEmpty() ? null : list.get(0))
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
}
