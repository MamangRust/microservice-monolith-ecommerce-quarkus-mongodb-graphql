package com.sanedge.user.seed;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Seeds the identity context: roles, users, and user→role assignments.
 * Order = 10 (first to run).
 *
 * <p>Idempotent: roles upsert on {@code roleName}, users upsert on
 * {@code username}. User→role assignment is stored as the embedded
 * {@code roleIds} set on each user document (Mongo replaces the SQL
 * {@code user_roles} join table).</p>
 */
public class IdentitySeeder implements Seeder {

    private static final List<String> ROLES = List.of(
            "Super Admin", "Admin", "Merchant Admin", "Merchant Operator",
            "Finance", "Compliance", "Auditor", "Support", "Viewer", "User"
    );

    private static final String[][] USERS = {
            {"Admin", "System", "admin", "admin@example.com"},
            {"Staff", "Operator", "staff", "staff@example.com"},
            {"John", "Doe", "user", "user@example.com"}
    };

    private static final Map<String, String> USER_ROLE = Map.of(
            "admin", "Admin",
            "staff", "Merchant Admin",
            "user", "User");

    @Override
    public String domain() {
        return "identity";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Uni<Void> seed(SeedContext ctx) {
        Logger log = ctx.log();
        ReactiveMongoDatabase identity = ctx.db("identity");
        ReactiveMongoCollection<Document> roles = identity.getCollection("roles", Document.class);
        ReactiveMongoCollection<Document> users = identity.getCollection("users", Document.class);

        return seedRoles(roles, log)
                .chain(() -> seedUsers(users, ctx.passwordHasher(), log))
                .chain(() -> seedUserRoles(roles, users, log))
                .replaceWithVoid();
    }

    private Uni<Void> seedRoles(ReactiveMongoCollection<Document> roles, Logger log) {
        Date now = Date.from(Instant.now());
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String role : ROLES) {
            chain = chain.chain(() -> roles.updateOne(
                    Filters.eq("roleName", role),
                    Updates.combine(
                            Updates.setOnInsert("roleName", role),
                            Updates.setOnInsert("createdAt", now),
                            Updates.setOnInsert("updatedAt", now),
                            Updates.setOnInsert("deletedAt", null)),
                    new UpdateOptions().upsert(true)).replaceWithVoid());
        }
        return chain.invoke(() -> log.infof("Roles seeded: %d roles", ROLES.size()));
    }

    private Uni<Void> seedUsers(ReactiveMongoCollection<Document> users,
                                Function<String, String> passwordHasher,
                                Logger log) {
        Date now = Date.from(Instant.now());
        String hashed = passwordHasher.apply("password");
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String[] u : USERS) {
            chain = chain.chain(() -> users.updateOne(
                    Filters.eq("username", u[2]),
                    Updates.combine(
                            Updates.setOnInsert("firstname", u[0]),
                            Updates.setOnInsert("lastname", u[1]),
                            Updates.setOnInsert("username", u[2]),
                            Updates.setOnInsert("email", u[3]),
                            Updates.setOnInsert("password", hashed),
                            Updates.setOnInsert("createdAt", now),
                            Updates.setOnInsert("updatedAt", now),
                            Updates.setOnInsert("deletedAt", null)),
                    new UpdateOptions().upsert(true)).replaceWithVoid());
        }
        return chain.invoke(() -> log.infof("Users seeded: %d users", USERS.length));
    }

    private Uni<Void> seedUserRoles(ReactiveMongoCollection<Document> roles,
                                    ReactiveMongoCollection<Document> users,
                                    Logger log) {
        List<String> roleNames = new ArrayList<>(USER_ROLE.values());
        return roles.find(Filters.in("roleName", roleNames)).collect().asList()
                .chain(roleDocs -> {
                    Map<String, ObjectId> roleIds = new java.util.HashMap<>();
                    for (Document role : roleDocs) {
                        roleIds.put(role.getString("roleName"), role.getObjectId("_id"));
                    }
                    Uni<Void> chain = Uni.createFrom().voidItem();
                    int assigned = 0;
                    for (Map.Entry<String, String> entry : USER_ROLE.entrySet()) {
                        ObjectId roleId = roleIds.get(entry.getValue());
                        if (roleId == null) {
                            continue;
                        }
                        assigned++;
                        chain = chain.chain(() -> users.updateOne(
                                Filters.eq("username", entry.getKey()),
                                Updates.set("roleIds", List.of(roleId)))
                                .replaceWithVoid());
                    }
                    final int count = assigned;
                    return chain.invoke(() -> log.infof("User roles assigned: %d users", count));
                });
    }
}
