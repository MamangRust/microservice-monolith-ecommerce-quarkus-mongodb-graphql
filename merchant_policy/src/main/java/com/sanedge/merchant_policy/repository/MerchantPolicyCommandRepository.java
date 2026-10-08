package com.sanedge.merchant_policy.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.merchant_policy.entity.MerchantPolicy;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantPolicyCommandRepository implements ReactivePanacheMongoRepository<MerchantPolicy> {

    public Uni<MerchantPolicy> findById(String merchantPolicyId) {
        return find("_id", new ObjectId(merchantPolicyId)).firstResult();
    }

    public Uni<MerchantPolicy> trash(String merchantPolicyId) {
        return find("_id", new ObjectId(merchantPolicyId)).firstResult()
                .chain(policy -> {
                    if (policy != null && policy.getDeletedAt() == null) {
                        policy.setDeletedAt(Instant.now());
                        return policy.update().map(v -> policy);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantPolicy> restore(String merchantPolicyId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantPolicyId)).firstResult()
                .chain(policy -> {
                    if (policy != null) {
                        policy.setDeletedAt(null);
                        return policy.update().map(v -> policy);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantPolicy> deletePermanent(String merchantPolicyId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantPolicyId)).firstResult()
                .chain(policy -> {
                    if (policy != null) {
                        return policy.delete().map(v -> policy);
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
