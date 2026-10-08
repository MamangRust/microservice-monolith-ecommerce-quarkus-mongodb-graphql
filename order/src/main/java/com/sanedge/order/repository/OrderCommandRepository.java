package com.sanedge.order.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.order.entity.Order;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderCommandRepository implements ReactivePanacheMongoRepository<Order> {

    public Uni<Order> findById(String orderId) {
        return find("_id", new ObjectId(orderId)).firstResult();
    }

    public Uni<Order> trashed(String orderId) {
        return find("_id", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null && order.getDeletedAt() == null) {
                        order.setDeletedAt(Instant.now());
                        return order.update().map(v -> order);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Order> restore(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null) {
                        order.setDeletedAt(null);
                        return order.update().map(v -> order);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Order> persistNew(Order order) {
        return persist(order);
    }

    public Uni<Long> updateTotalPrice(String orderId, Integer totalPrice) {
        return update("{ '$set': { 'totalPrice': ?1 } }", totalPrice)
                .where("{ '_id': ?1 }", new ObjectId(orderId));
    }

    public Uni<Boolean> deleteCreated(String orderId) {
        return delete("{ '_id': ?1 }", new ObjectId(orderId)).map(count -> count > 0);
    }

    public Uni<Order> deletePermanent(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderId)).firstResult()
                .chain(order -> {
                    if (order != null) {
                        return order.delete().map(v -> order);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<java.util.List<Order>> findAllDeleted() {
        return list("{ 'deletedAt': { '$ne': null } }");
    }

    public Uni<Boolean> restoreAllDeleted() {
        return update("{ '$set': { 'deletedAt': null } }")
                .where("{ 'deletedAt': { '$ne': null } }")
                .map(updatedCount -> updatedCount > 0);
    }

    public Uni<Boolean> deleteAllDeleted() {
        return delete("{ 'deletedAt': { '$ne': null } }")
                .map(deletedCount -> deletedCount > 0);
    }
}
