package com.sanedge.review.service.impl;

import java.time.Instant;
import java.util.Set;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.review.domain.requests.CreateReviewRequest;
import com.sanedge.review.domain.requests.UpdateReviewRequest;
import com.sanedge.review.domain.response.ReviewResponse;
import com.sanedge.review.domain.response.ReviewResponseDeleteAt;
import com.sanedge.review.entity.Review;
import com.sanedge.review.repository.ReviewCommandRepository;
import com.sanedge.review.repository.ReviewQueryRepository;
import com.sanedge.review.service.ReviewCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class ReviewCommandServiceImpl implements ReviewCommandService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewCommandServiceImpl.class);

    private final ReviewQueryRepository reviewQueryRepository;
    private final ReviewCommandRepository reviewCommandRepository;
    private final Validator validator;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    @Inject
    public ReviewCommandServiceImpl(ReviewQueryRepository reviewQueryRepository,
            ReviewCommandRepository reviewCommandRepository,
            Validator validator,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.reviewQueryRepository = reviewQueryRepository;
        this.reviewCommandRepository = reviewCommandRepository;
        this.validator = validator;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateCache(String reviewId) {
        if (reviewId != null) {
            return redisService.deleteReactive("review:id:" + reviewId).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<ApiResponse<ReviewResponse>> create(CreateReviewRequest request) {
        logger.info("Creating review for productId={}, userId={}", request.getProductId(), request.getUserId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            logger.error("Validation failed for create review", e);
            return Uni.createFrom().failure(e);
        }

        Review review = new Review();
        review.setUserId(new ObjectId(request.getUserId()));
        review.setProductId(new ObjectId(request.getProductId()));
        review.setName(request.getName());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        Instant now = Instant.now();
        review.setCreatedAt(now);
        review.setUpdatedAt(now);

        Attributes attributes = Attributes.builder()
                .put("product.id", String.valueOf(request.getProductId()))
                .put("user.id", String.valueOf(request.getUserId()))
                .build();

        return tracingMetrics.traceAndMeasure("createReview", "create_review", attributes,
                () -> reviewCommandRepository.persist(review)
                        .chain(saved -> {
                            ReviewResponse response = ReviewResponse.from(saved);

                            return invalidateCache(saved.id != null ? saved.id.toHexString() : null)
                                    .map(v -> {
                                        logger.info("Review created successfully id={}", saved.id);
                                        return ApiResponse.success("Review created successfully!", response);
                                    });
                        })
                        .onFailure().invoke(e -> logger.error("Failed to create review", e)));
    }

    @Override
    public Uni<ApiResponse<ReviewResponse>> update(UpdateReviewRequest request) {
        logger.info("Updating review id={}", request.getReviewId());

        try {
            validateRequest(request);
            if (request.getReviewId() == null) {
                throw new ResourceNotFoundException("review_id is required");
            }
        } catch (Exception e) {
            logger.error("Validation failed for update review", e);
            return Uni.createFrom().failure(e);
        }

        return tracingMetrics.traceAndMeasure("updateReview", "update_review",
                Attributes.builder().put("review.id", request.getReviewId()).build(),
                () -> reviewQueryRepository.findReviewById(request.getReviewId())
                        .chain(optReview -> {
                            if (optReview.isEmpty()) {
                                throw new ResourceNotFoundException("Review not found");
                            }
                            Review review = optReview.get();
                            review.setComment(request.getComment());
                            review.setName(request.getName());
                            review.setRating(request.getRating());
                            review.setUpdatedAt(Instant.now());

                            return reviewCommandRepository.update(review);
                        })
                        .chain(updated -> {
                            ReviewResponse response = ReviewResponse.from(updated);

                            return invalidateCache(updated.id != null ? updated.id.toHexString() : null)
                                    .map(v -> {
                                        logger.info("Review updated successfully id={}", updated.id);
                                        return ApiResponse.success("Review updated successfully!", response);
                                    });
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to update review id={}", request.getReviewId(), e)));
    }

    @Override
    public Uni<ApiResponse<ReviewResponseDeleteAt>> trash(String id) {
        logger.info("Trashing review id={}", id);

        return tracingMetrics.traceAndMeasure("trashReview", "trash_review",
                Attributes.builder().put("review.id", id).build(),
                () -> reviewCommandRepository.trash(id)
                        .chain(review -> {
                            if (review == null) {
                                throw new ResourceNotFoundException("Review not found or already trashed");
                            }
                            ReviewResponseDeleteAt response = ReviewResponseDeleteAt.from(review);

                            return invalidateCache(id)
                                    .map(v -> {
                                        logger.info("Successfully trashed review with ID: {}", id);
                                        return ApiResponse.success("Review trashed successfully!", response);
                                    });
                        })
                        .onFailure().invoke(e -> logger.error("Failed to trash review id={}", id, e)));
    }

    @Override
    public Uni<ApiResponse<ReviewResponseDeleteAt>> restore(String id) {
        logger.info("Restoring review id={}", id);

        return tracingMetrics.traceAndMeasure("restoreReview", "restore_review",
                Attributes.builder().put("review.id", id).build(),
                () -> reviewCommandRepository.restore(id)
                        .chain(review -> {
                            if (review == null) {
                                throw new ResourceNotFoundException("Review not found or not trashed");
                            }
                            ReviewResponseDeleteAt response = ReviewResponseDeleteAt.from(review);

                            return invalidateCache(id)
                                    .map(v -> {
                                        logger.info("Successfully restored review with ID: {}", id);
                                        return ApiResponse.success("Review restored successfully!", response);
                                    });
                        })
                        .onFailure().invoke(e -> logger.error("Failed to restore review id={}", id, e)));
    }

    @Override
    public Uni<ApiResponse<Void>> delete(String id) {
        Attributes attrs = Attributes.builder().put("review.id", id).build();
        logger.warn("Permanently deleting review id={}", id);

        return tracingMetrics.traceAndMeasure("deleteReviewPermanent", "delete_review_permanent", attrs, () -> {
            return reviewCommandRepository.deletePermanent(id)
                    .chain(deletedReview -> {
                        if (deletedReview == null) {
                            logger.warn("Permanent delete failed - review not found or must be trashed before permanent deletion with id: {}", id);
                            throw new InvalidRequestException("Review not found or must be trashed before permanent deletion");
                        }

                        return invalidateCache(id)
                                .map(v2 -> {
                                    logger.info("Successfully permanently deleted review with ID: {}", id);
                                    return ApiResponse.success("Review permanently deleted!");
                                });
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> restoreAll() {
        logger.info("Restoring ALL trashed reviews");

        return tracingMetrics.traceAndMeasure("restoreAllReviews", "restore_all_reviews", () -> {
            return reviewCommandRepository.restoreAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed reviews found");
                        }
                        logger.info("Successfully restored all trashed reviews");
                        return ApiResponse.success("All reviews restored successfully!");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> deleteAll() {
        logger.warn("Permanently deleting ALL trashed reviews");

        return tracingMetrics.traceAndMeasure("deleteAllReviewsPermanent", "delete_all_reviews_permanent", () -> {
            return reviewCommandRepository.deleteAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed reviews found");
                        }
                        logger.info("Successfully permanently deleted all trashed reviews");
                        return ApiResponse.success("All reviews permanently deleted!");
                    });
        });
    }
}
