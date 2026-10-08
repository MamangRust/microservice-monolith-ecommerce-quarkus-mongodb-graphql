package com.sanedge.banner.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.banner.entity.Banner;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BannerCommandRepository implements ReactivePanacheMongoRepository<Banner> {

    public Uni<Banner> findById(String bannerId) {
        return find("_id", new ObjectId(bannerId)).firstResult();
    }

    public Uni<Banner> trash(String bannerId) {
        return find("_id", new ObjectId(bannerId)).firstResult()
                .chain(banner -> {
                    if (banner != null && banner.getDeletedAt() == null) {
                        banner.setDeletedAt(Instant.now());
                        return banner.update().map(v -> banner);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Banner> restore(String bannerId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(bannerId)).firstResult()
                .chain(banner -> {
                    if (banner != null) {
                        banner.setDeletedAt(null);
                        return banner.update().map(v -> banner);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Banner> deletePermanent(String bannerId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(bannerId)).firstResult()
                .chain(banner -> {
                    if (banner != null) {
                        return banner.delete().map(v -> banner);
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
