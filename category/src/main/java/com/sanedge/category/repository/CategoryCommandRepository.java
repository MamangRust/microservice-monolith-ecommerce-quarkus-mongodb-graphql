package com.sanedge.category.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.category.entity.Category;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryCommandRepository implements ReactivePanacheMongoRepository<Category> {

    public Uni<Category> trash(String categoryId) {
        return find("_id", new ObjectId(categoryId)).firstResult()
                .chain(category -> {
                    if (category != null && category.getDeletedAt() == null) {
                        category.setDeletedAt(Instant.now());
                        return category.update().map(v -> category);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Category> restore(String categoryId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(categoryId)).firstResult()
                .chain(category -> {
                    if (category != null) {
                        category.setDeletedAt(null);
                        return category.update().map(v -> category);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Category> deletePermanent(String categoryId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(categoryId)).firstResult()
                .chain(category -> {
                    if (category != null) {
                        return category.delete().map(v -> category);
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
