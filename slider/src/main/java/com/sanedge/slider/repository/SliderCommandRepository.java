package com.sanedge.slider.repository;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sanedge.slider.entity.Slider;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SliderCommandRepository implements ReactivePanacheMongoRepository<Slider> {

    public Uni<Slider> findById(String sliderId) {
        return find("_id", new ObjectId(sliderId)).firstResult();
    }

    public Uni<Slider> trashed(String sliderId) {
        return find("_id", new ObjectId(sliderId)).firstResult()
                .chain(slider -> {
                    if (slider != null && slider.getDeletedAt() == null) {
                        slider.setDeletedAt(Instant.now());
                        return slider.update().map(v -> slider);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Slider> restore(String sliderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(sliderId)).firstResult()
                .chain(slider -> {
                    if (slider != null) {
                        slider.setDeletedAt(null);
                        return slider.update().map(v -> slider);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Slider> deletePermanent(String sliderId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(sliderId)).firstResult()
                .chain(slider -> {
                    if (slider != null) {
                        return slider.delete().map(v -> slider);
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
