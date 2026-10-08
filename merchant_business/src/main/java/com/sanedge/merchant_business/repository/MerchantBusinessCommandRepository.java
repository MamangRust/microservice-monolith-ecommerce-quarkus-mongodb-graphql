package com.sanedge.merchant_business.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.merchant_business.entity.MerchantBusinessInformation;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantBusinessCommandRepository implements ReactivePanacheMongoRepository<MerchantBusinessInformation> {

    public Uni<MerchantBusinessInformation> findById(String merchantBusinessId) {
        return find("_id", new ObjectId(merchantBusinessId)).firstResult();
    }

    public Uni<MerchantBusinessInformation> trashed(String merchantBusinessId) {
        return find("_id", new ObjectId(merchantBusinessId)).firstResult()
                .chain(info -> {
                    if (info != null && info.getDeletedAt() == null) {
                        info.setDeletedAt(Instant.now());
                        return info.update().map(v -> info);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantBusinessInformation> restore(String merchantBusinessId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantBusinessId)).firstResult()
                .chain(info -> {
                    if (info != null) {
                        info.setDeletedAt(null);
                        return info.update().map(v -> info);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantBusinessInformation> deletePermanent(String merchantBusinessId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantBusinessId)).firstResult()
                .chain(info -> {
                    if (info != null) {
                        return info.delete().map(v -> info);
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
