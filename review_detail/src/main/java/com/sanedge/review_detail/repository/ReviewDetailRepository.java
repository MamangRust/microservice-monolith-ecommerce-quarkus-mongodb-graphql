package com.sanedge.review_detail.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.review_detail.entity.ReviewDetail;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReviewDetailRepository implements ReactivePanacheMongoRepository<ReviewDetail> {

    public Uni<ReviewDetail> findById(String reviewDetailId) {
        return find("_id", new ObjectId(reviewDetailId)).firstResult();
    }

    public Uni<ReviewDetail> trash(String reviewDetailId) {
        return find("_id", new ObjectId(reviewDetailId)).firstResult()
                .chain(detail -> {
                    if (detail != null && detail.getDeletedAt() == null) {
                        detail.setDeletedAt(Instant.now());
                        return detail.update().map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<ReviewDetail> restore(String reviewDetailId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(reviewDetailId)).firstResult()
                .chain(detail -> {
                    if (detail != null) {
                        detail.setDeletedAt(null);
                        return detail.update().map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<ReviewDetail> deletePermanent(String reviewDetailId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(reviewDetailId)).firstResult()
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
