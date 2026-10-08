package com.sanedge.review.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.review.entity.Review;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReviewCommandRepository implements ReactivePanacheMongoRepository<Review> {

    public Uni<Review> findById(String reviewId) {
        return find("_id", new ObjectId(reviewId)).firstResult();
    }

    public Uni<Review> trash(String reviewId) {
        return find("_id", new ObjectId(reviewId)).firstResult()
                .chain(review -> {
                    if (review != null && review.getDeletedAt() == null) {
                        review.setDeletedAt(Instant.now());
                        return review.update().map(v -> review);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Review> restore(String reviewId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(reviewId)).firstResult()
                .chain(review -> {
                    if (review != null) {
                        review.setDeletedAt(null);
                        return review.update().map(v -> review);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Review> deletePermanent(String reviewId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(reviewId)).firstResult()
                .chain(review -> {
                    if (review != null) {
                        return review.delete().map(v -> review);
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
