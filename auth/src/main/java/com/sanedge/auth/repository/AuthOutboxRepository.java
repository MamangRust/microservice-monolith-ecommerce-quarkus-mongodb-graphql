package com.sanedge.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.sanedge.auth.entity.AuthOutbox;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthOutboxRepository implements ReactivePanacheMongoRepository<AuthOutbox> {
    @ConfigProperty(name = "auth.outbox.lease.seconds", defaultValue = "300")
    long leaseSeconds;

    public Uni<Long> countPending() {
        return count("{ 'status': 'PENDING' }");
    }

    public Uni<List<AuthOutbox>> findDue(int limit) {
        Instant now = Instant.now();
        Instant leaseCutoff = now.minusSeconds(Math.max(1, leaseSeconds));
        return find("{ 'status': 'PENDING', '$and': [ "
                + "{ '$or': [ { 'nextAttemptAt': null }, { 'nextAttemptAt': { '$lte': ?1 } } ] }, "
                + "{ '$or': [ { 'claimedAt': null }, { 'claimedAt': { '$lt': ?2 } } ] } ] }",
                Sort.ascending("createdAt"), now, leaseCutoff)
                .page(0, limit)
                .list();
    }

    public Uni<AuthOutbox> claim(AuthOutbox event) {
        Instant now = Instant.now();
        Instant leaseCutoff = now.minusSeconds(Math.max(1, leaseSeconds));
        String claimToken = UUID.randomUUID().toString();
        return update("{ '$set': { 'claimedAt': ?1, 'claimToken': ?2 }, '$inc': { 'attempts': 1 } }",
                now, claimToken)
                .where("{ '_id': ?1, 'status': 'PENDING', '$and': [ "
                        + "{ '$or': [ { 'claimedAt': null }, { 'claimedAt': { '$lt': ?2 } } ] }, "
                        + "{ '$or': [ { 'nextAttemptAt': null }, { 'nextAttemptAt': { '$lte': ?3 } } ] } ] }",
                        event.id, leaseCutoff, now)
                .chain(updated -> updated > 0 ? findById(event.id) : Uni.<AuthOutbox>createFrom().nullItem());
    }

    public Uni<Void> markSent(AuthOutbox event) {
        return update("{ '$set': { 'status': 'SENT', 'claimedAt': null, 'lastError': null } }")
                .where("{ '_id': ?1, 'status': 'PENDING', 'claimToken': ?2 }", event.id, event.getClaimToken())
                .replaceWithVoid();
    }

    public Uni<Void> markRetry(AuthOutbox event, Throwable failure, long delaySeconds) {
        return update("{ '$set': { 'status': 'PENDING', 'claimedAt': null, 'lastError': ?1, 'nextAttemptAt': ?2 } }",
                failure == null ? "unknown failure" : failure.getMessage(),
                Instant.now().plusSeconds(delaySeconds))
                .where("{ '_id': ?1, 'status': 'PENDING', 'claimToken': ?2 }", event.id, event.getClaimToken())
                .replaceWithVoid();
    }
}
