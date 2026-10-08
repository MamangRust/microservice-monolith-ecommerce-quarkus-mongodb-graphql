package com.sanedge.order.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.order.entity.ShippingAddress;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingAddressCommandRepository implements ReactivePanacheMongoRepository<ShippingAddress> {

    public Uni<ShippingAddress> findById(String shippingAddressId) {
        return find("_id", new ObjectId(shippingAddressId)).firstResult();
    }

    public Uni<ShippingAddress> trashed(String shippingAddressId) {
        return find("_id", new ObjectId(shippingAddressId)).firstResult()
                .chain(address -> {
                    if (address != null && address.getDeletedAt() == null) {
                        address.setDeletedAt(Instant.now());
                        return address.update().map(v -> address);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<ShippingAddress> restore(String shippingAddressId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(shippingAddressId)).firstResult()
                .chain(address -> {
                    if (address != null) {
                        address.setDeletedAt(null);
                        return address.update().map(v -> address);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<ShippingAddress> deletePermanent(String shippingAddressId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(shippingAddressId)).firstResult()
                .chain(address -> {
                    if (address != null) {
                        return address.delete().map(v -> address);
                    }
                    return Uni.createFrom().nullItem();
                });
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
