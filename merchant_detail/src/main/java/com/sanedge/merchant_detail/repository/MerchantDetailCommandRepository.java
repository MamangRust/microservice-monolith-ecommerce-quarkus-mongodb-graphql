package com.sanedge.merchant_detail.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.merchant_detail.entity.MerchantDetail;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantDetailCommandRepository implements ReactivePanacheMongoRepository<MerchantDetail> {

    public Uni<MerchantDetail> findById(String merchantDetailId) {
        return find("_id", new ObjectId(merchantDetailId)).firstResult();
    }

    public Uni<MerchantDetail> trashed(String merchantDetailId) {
        return find("_id", new ObjectId(merchantDetailId)).firstResult()
                .chain(detail -> {
                    if (detail != null && detail.getDeletedAt() == null) {
                        detail.setDeletedAt(Instant.now());
                        return detail.update().map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantDetail> restore(String merchantDetailId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantDetailId)).firstResult()
                .chain(detail -> {
                    if (detail != null) {
                        detail.setDeletedAt(null);
                        return detail.update().map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantDetail> deletePermanent(String merchantDetailId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantDetailId)).firstResult()
                .chain(detail -> {
                    if (detail != null) {
                        return detail.delete().map(v -> detail);
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
