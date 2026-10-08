package com.sanedge.review_detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.review_detail.entity.ReviewDetail;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ReviewDetailRepositoryTest extends MongoTestBase {

    private static final String REVIEW_ID = "507f1f77bcf86cd799439011";
    private static final String MISSING_ID = "507f1f77bcf86cd799439099";

    @Inject
    ReviewDetailRepository reviewDetailRepository;

    private Uni<ReviewDetail> createAndPersistDetail(String reviewId, String type, String url) {
        ReviewDetail detail = new ReviewDetail();
        detail.setReviewId(new ObjectId(reviewId));
        detail.setType(type);
        detail.setUrl(url);
        detail.setCaption("Test caption for " + type);
        Instant now = Instant.now();
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        return reviewDetailRepository.persist(detail).replaceWith(detail);
    }

    @Test
    void testTrashReviewDetail() {
        createAndPersistDetail(REVIEW_ID, "IMAGE", "http://example.com/img.jpg")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getDeletedAt()).isNull();
                })
                .chain(saved -> reviewDetailRepository.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashReviewDetailReturnsNullIfAlreadyTrashed() {
        createAndPersistDetail(REVIEW_ID, "VIDEO", "http://example.com/vid.mp4")
                .chain(saved -> reviewDetailRepository.trash(saved.id.toHexString())
                        .chain(ignored -> reviewDetailRepository.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashReviewDetailReturnsNullIfNotFound() {
        reviewDetailRepository.trash(MISSING_ID)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreReviewDetail() {
        createAndPersistDetail(REVIEW_ID, "IMAGE", "http://example.com/img2.jpg")
                .chain(saved -> reviewDetailRepository.trash(saved.id.toHexString())
                        .chain(ignored -> reviewDetailRepository.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreReviewDetailReturnsNullIfNotTrashed() {
        createAndPersistDetail(REVIEW_ID, "IMAGE", "http://example.com/img3.jpg")
                .chain(saved -> reviewDetailRepository.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistDetail(REVIEW_ID, "IMAGE", "http://example.com/img4.jpg")
                .chain(saved -> reviewDetailRepository.trash(saved.id.toHexString())
                        .chain(ignored -> reviewDetailRepository.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return reviewDetailRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistDetail(REVIEW_ID, "IMAGE", "http://example.com/img5.jpg")
                .chain(saved -> reviewDetailRepository.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return reviewDetailRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistDetail(REVIEW_ID, "IMAGE", "url1.jpg"),
                        createAndPersistDetail(REVIEW_ID, "VIDEO", "url2.mp4"),
                        createAndPersistDetail("507f1f77bcf86cd799439012", "IMAGE", "url3.jpg"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        reviewDetailRepository.trash(tuple.getItem1().id.toHexString()),
                        reviewDetailRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> reviewDetailRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    reviewDetailRepository.findById(tuple.getItem1().id.toHexString()),
                                    reviewDetailRepository.findById(tuple.getItem2().id.toHexString()),
                                    reviewDetailRepository.findById(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .invoke(tuple -> {
                    assertThat(tuple.getItem1().getDeletedAt()).isNull();
                    assertThat(tuple.getItem2().getDeletedAt()).isNull();
                    assertThat(tuple.getItem3().getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistDetail(REVIEW_ID, "IMAGE", "url4.jpg"),
                        createAndPersistDetail(REVIEW_ID, "VIDEO", "url5.mp4"),
                        createAndPersistDetail("507f1f77bcf86cd799439012", "IMAGE", "url6.jpg"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        reviewDetailRepository.trash(tuple.getItem1().id.toHexString()),
                        reviewDetailRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> reviewDetailRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    reviewDetailRepository.findById(tuple.getItem1().id.toHexString()),
                                    reviewDetailRepository.findById(tuple.getItem2().id.toHexString()),
                                    reviewDetailRepository.findById(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures();
                        }))
                .invoke(fetched -> {
                    assertThat(fetched.get(0)).isNull();
                    assertThat(fetched.get(1)).isNull();
                    assertThat(fetched.get(2)).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
