package com.sanedge.merchant_award.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantAwardCommandRepository implements ReactivePanacheMongoRepository<MerchantCertificationAndAward> {

    public Uni<MerchantCertificationAndAward> findById(String merchantAwardId) {
        return find("_id", new ObjectId(merchantAwardId)).firstResult();
    }

    public Uni<MerchantCertificationAndAward> trashed(String merchantAwardId) {
        return find("_id", new ObjectId(merchantAwardId)).firstResult()
                .chain(award -> {
                    if (award != null && award.getDeletedAt() == null) {
                        award.setDeletedAt(Instant.now());
                        return award.update().map(v -> award);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantCertificationAndAward> restore(String merchantAwardId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantAwardId)).firstResult()
                .chain(award -> {
                    if (award != null) {
                        award.setDeletedAt(null);
                        return award.update().map(v -> award);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantCertificationAndAward> deletePermanent(String merchantAwardId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantAwardId)).firstResult()
                .chain(award -> {
                    if (award != null) {
                        return award.delete().map(v -> award);
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
