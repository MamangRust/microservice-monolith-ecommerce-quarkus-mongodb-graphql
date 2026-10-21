package com.sanedge.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sanedge.order.entity.OrderOutbox;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderOutboxRepository implements ReactivePanacheMongoRepository<OrderOutbox> {

    private static final long LEASE_SECONDS = 300;

    /**
     * Persists a batch of stats events. The single-document {@code persist}
     * inherited from Panache would need one round trip per event; a single
     * {@code insertMany} keeps an order creation (1 order + N items) cheap.
     */
    public Uni<Void> persistPending(java.util.List<OrderOutbox> events) {
        if (events == null || events.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        // BaseModel#onCreate is not a Panache lifecycle callback (it is never
        // invoked) and reads of findDue order by createdAt, so stamp it here.
        Instant now = Instant.now();
        for (OrderOutbox event : events) {
            if (event.id == null) {
                event.id = new ObjectId();
            }
            if (event.getCreatedAt() == null) {
                event.setCreatedAt(now);
                event.setUpdatedAt(now);
            }
        }
        return persist(events).replaceWithVoid();
    }

    public Uni<Long> countPending() {
        return count("{ 'status': 'PENDING' }");
    }

    public Uni<List<OrderOutbox>> findDue(int limit) {
        Instant now = Instant.now();
        Instant leaseCutoff = now.minusSeconds(LEASE_SECONDS);
        return find("{ 'status': 'PENDING', '$and': [ "
                + "{ '$or': [ { 'nextAttemptAt': null }, { 'nextAttemptAt': { '$lte': ?1 } } ] }, "
                + "{ '$or': [ { 'claimedAt': null }, { 'claimedAt': { '$lt': ?2 } } ] } ] }",
                Sort.ascending("createdAt"), now, leaseCutoff)
                .page(0, limit)
                .list();
    }

    public Uni<OrderOutbox> claim(OrderOutbox event) {
        Instant now = Instant.now();
        Instant leaseCutoff = now.minusSeconds(LEASE_SECONDS);
        String claimToken = UUID.randomUUID().toString();
        return update("{ '$set': { 'claimedAt': ?1, 'claimToken': ?2 }, '$inc': { 'attempts': 1 } }",
                now, claimToken)
                .where("{ '_id': ?1, 'status': 'PENDING', '$and': [ "
                        + "{ '$or': [ { 'claimedAt': null }, { 'claimedAt': { '$lt': ?2 } } ] }, "
                        + "{ '$or': [ { 'nextAttemptAt': null }, { 'nextAttemptAt': { '$lte': ?3 } } ] } ] }",
                        event.id, leaseCutoff, now)
                .chain(updated -> updated > 0 ? findById(event.id) : Uni.<OrderOutbox>createFrom().nullItem());
    }

    public Uni<Void> markSent(OrderOutbox event) {
        return update("{ '$set': { 'status': 'SENT', 'claimedAt': null, 'lastError': null } }")
                .where("{ '_id': ?1, 'status': 'PENDING', 'claimToken': ?2 }", event.id, event.getClaimToken())
                .replaceWithVoid();
    }

    public Uni<Void> markRetry(OrderOutbox event, Throwable failure, long delaySeconds) {
        return update("{ '$set': { 'status': 'PENDING', 'claimedAt': null, 'lastError': ?1, 'nextAttemptAt': ?2 } }",
                failure == null ? "unknown failure" : failure.getMessage(),
                Instant.now().plusSeconds(delaySeconds))
                .where("{ '_id': ?1, 'status': 'PENDING', 'claimToken': ?2 }", event.id, event.getClaimToken())
                .replaceWithVoid();
    }
}
