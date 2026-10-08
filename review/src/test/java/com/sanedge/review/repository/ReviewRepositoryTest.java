package com.sanedge.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.entity.Review;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ReviewRepositoryTest extends MongoTestBase {

    private static final String USER_ID = "507f1f77bcf86cd799439011";
    private static final String PRODUCT_ID = "507f1f77bcf86cd799439012";

    @Inject
    ReviewQueryRepository reviewQueryRepo;

    @Inject
    ReviewCommandRepository reviewCommandRepo;

    private Uni<Review> createAndPersistReview(String name, String comment, String productId) {
        Review review = new Review();
        review.setUserId(new ObjectId(USER_ID));
        review.setProductId(new ObjectId(productId));
        review.setName(name);
        review.setComment(comment);
        review.setRating(5);
        Instant now = Instant.now();
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        return reviewQueryRepo.persist(review).replaceWith(review);
    }

    private FindAllReview buildFindAllRequest(int page, int pageSize, String search) {
        FindAllReview req = new FindAllReview();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistReview("John Doe", "Great product!", PRODUCT_ID)
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("John Doe");
                })
                .chain(saved -> reviewQueryRepo.findReviewById(saved.id.toHexString()))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getComment()).isEqualTo("Great product!");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewByIdReturnsEmptyWhenNotFound() {
        reviewQueryRepo.findReviewById("507f1f77bcf86cd799439099")
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashReview() {
        createAndPersistReview("Jane Doe", "Nice item", PRODUCT_ID)
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> reviewCommandRepo.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashReviewReturnsNullIfAlreadyTrashed() {
        createAndPersistReview("Alice", "Okay", PRODUCT_ID)
                .chain(saved -> reviewCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> reviewCommandRepo.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreReview() {
        createAndPersistReview("Bob", "Good", PRODUCT_ID)
                .chain(saved -> reviewCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> reviewCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistReview("Charlie", "Bad", PRODUCT_ID)
                .chain(saved -> reviewCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> reviewCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return reviewQueryRepo.findReviewById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistReview("R1", "C1", PRODUCT_ID),
                        createAndPersistReview("R2", "C2", PRODUCT_ID))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        reviewCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        reviewCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> reviewCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    reviewQueryRepo.findReviewById(tuple.getItem1().id.toHexString()),
                                    reviewQueryRepo.findReviewById(tuple.getItem2().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistReview("D1", "C1", PRODUCT_ID),
                        createAndPersistReview("D2", "C2", PRODUCT_ID))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        reviewCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        reviewCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> reviewCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    reviewQueryRepo.findReviewById(tuple.getItem1().id.toHexString()),
                                    reviewQueryRepo.findReviewById(tuple.getItem2().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewsExcludesTrashed() {
        Uni.combine().all()
                .unis(createAndPersistReview("Active Review", "Good", PRODUCT_ID),
                        createAndPersistReview("Trashed Review", "Bad", PRODUCT_ID))
                .asTuple()
                .chain(tuple -> reviewCommandRepo.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllReview req = buildFindAllRequest(1, 10, null);
                    return reviewQueryRepo.findReviews(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Review");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedReviews() {
        Uni.combine().all()
                .unis(createAndPersistReview("Trashed Only", "Bad", PRODUCT_ID),
                        createAndPersistReview("Not Trashed", "Good", PRODUCT_ID))
                .asTuple()
                .chain(tuple -> reviewCommandRepo.trash(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllReview req = buildFindAllRequest(1, 10, null);
                    return reviewQueryRepo.findTrashedReviews(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewsWithSearchKeyword() {
        Uni.join().all(
                createAndPersistReview("Excellent product", "Loved it", PRODUCT_ID),
                createAndPersistReview("Excellent service", "Fast delivery", PRODUCT_ID),
                createAndPersistReview("Average item", "Okay", PRODUCT_ID))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllReview req = buildFindAllRequest(1, 10, "Excellent");
                    return reviewQueryRepo.findReviews(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewsWithPagination() {
        Uni.join().all(
                createAndPersistReview("Page1", "C1", PRODUCT_ID),
                createAndPersistReview("Page2", "C2", PRODUCT_ID),
                createAndPersistReview("Page3", "C3", PRODUCT_ID),
                createAndPersistReview("Page4", "C4", PRODUCT_ID),
                createAndPersistReview("Page5", "C5", PRODUCT_ID))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllReview reqPage1 = buildFindAllRequest(1, 2, null);
                    return reviewQueryRepo.findReviews(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllReview reqPage2 = buildFindAllRequest(2, 2, null);
                    return reviewQueryRepo.findReviews(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getName()).isNotIn(
                                        page1.getData().get(0).getName(),
                                        page1.getData().get(1).getName());
                            });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewsWithNullSearchReturnsAll() {
        Uni.join().all(
                createAndPersistReview("Item A", "Comment A", PRODUCT_ID),
                createAndPersistReview("Item B", "Comment B", PRODUCT_ID))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllReview req = buildFindAllRequest(1, 10, null);
                    return reviewQueryRepo.findReviews(req);
                })
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindReviewsSearchReturnsEmptyWhenNoMatch() {
        createAndPersistReview("Summer Sale", "Good", PRODUCT_ID)
                .chain(ignored -> {
                    FindAllReview req = buildFindAllRequest(1, 10, "Winter");
                    return reviewQueryRepo.findReviews(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
