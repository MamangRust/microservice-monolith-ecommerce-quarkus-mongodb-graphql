package com.sanedge.merchant_detail.repository;

import java.time.Instant;
import java.util.Optional;

import org.bson.types.ObjectId;

import com.sanedge.merchant_detail.entity.MerchantSocialMediaLink;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantSocialMediaLinkRepository implements ReactivePanacheMongoRepository<MerchantSocialMediaLink> {

    public Uni<MerchantSocialMediaLink> findById(String merchantSocialId) {
        return find("_id", new ObjectId(merchantSocialId)).firstResult();
    }

    public Uni<MerchantSocialMediaLink> trashed(String merchantSocialId) {
        return find("_id", new ObjectId(merchantSocialId)).firstResult()
                .chain(socialLink -> {
                    if (socialLink != null && socialLink.getDeletedAt() == null) {
                        socialLink.setDeletedAt(Instant.now());
                        return socialLink.update().map(v -> socialLink);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantSocialMediaLink> restore(String merchantSocialId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantSocialId)).firstResult()
                .chain(socialLink -> {
                    if (socialLink != null) {
                        socialLink.setDeletedAt(null);
                        return socialLink.update().map(v -> socialLink);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<MerchantSocialMediaLink> deletePermanent(String merchantSocialId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(merchantSocialId)).firstResult()
                .chain(socialLink -> {
                    if (socialLink != null) {
                        return socialLink.delete().map(v -> socialLink);
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

    public Uni<Optional<MerchantSocialMediaLink>> findByMerchantDetailIdAndPlatform(ObjectId merchantDetailId,
            String platform) {
        return find("{ 'merchantDetailId': ?1, 'platform': ?2, 'deletedAt': null }", merchantDetailId, platform)
                .firstResult()
                .map(Optional::ofNullable);
    }
}
