package com.sanedge.review_detail.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.review_detail.domain.requests.CreateReviewDetailRequest;
import com.sanedge.review_detail.domain.requests.UpdateReviewDetailRequest;
import com.sanedge.review_detail.domain.response.ReviewDetailResponse;
import com.sanedge.review_detail.domain.response.ReviewDetailResponseDeleteAt;
import com.sanedge.review_detail.entity.ReviewDetail;
import com.sanedge.review_detail.repository.ReviewDetailRepository;
import com.sanedge.review_detail.service.ReviewDetailService;

import io.opentelemetry.api.common.Attributes;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class ReviewDetailServiceImpl implements ReviewDetailService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewDetailServiceImpl.class);

    private final ReviewDetailRepository reviewDetailRepository;
    private final Validator validator;
    private final TracingMetrics tracingMetrics;

    @Inject
    public ReviewDetailServiceImpl(ReviewDetailRepository reviewDetailRepository,
            Validator validator,
            TracingMetrics tracingMetrics) {
        this.reviewDetailRepository = reviewDetailRepository;
        this.validator = validator;
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

    @Override
    public Uni<ApiResponse<List<ReviewDetailResponse>>> create(List<CreateReviewDetailRequest> requests) {
        try {
            validateRequest(requests);
        } catch (Exception e) {
            logger.error("Validation failed for create review details", e);
            return Uni.createFrom().failure(e);
        }

        logger.info("Creating review details batch with size: {}", requests.size());

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (CreateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = Uni.createFrom().item(() -> {
                logger.info("Creating review detail for reviewId={} type={}", req.getReviewId(), req.getType());

                ReviewDetail reviewDetail = new ReviewDetail();
                reviewDetail.setReviewId(new ObjectId(req.getReviewId()));
                reviewDetail.setType(req.getType());
                reviewDetail.setUrl(req.getFile());
                reviewDetail.setCaption(req.getCaption());
                Instant now = Instant.now();
                reviewDetail.setCreatedAt(now);
                reviewDetail.setUpdatedAt(now);
                return reviewDetail;
            }).chain(reviewDetail -> reviewDetailRepository.persist(reviewDetail)
                    .map(ReviewDetailResponse::from));

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return tracingMetrics.traceAndMeasure("createReviewDetails", "create_review_details",
                () -> Uni.join().all(unis).andCollectFailures()
                        .map(responses -> {
                            logger.info("Successfully created {} review details", responses.size());
                            return ApiResponse.success("Review details created successfully!", responses);
                        })
                        .onFailure().invoke(e -> logger.error("Failed to create review details", e)));
    }

    @Override
    public Uni<ApiResponse<List<ReviewDetailResponse>>> update(List<UpdateReviewDetailRequest> requests) {
        try {
            validateRequest(requests);
        } catch (Exception e) {
            logger.error("Validation failed for update review details", e);
            return Uni.createFrom().failure(e);
        }

        logger.info("Updating review details batch with size: {}", requests.size());

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (UpdateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = reviewDetailRepository.findById(req.getReviewDetailId())
                    .chain(reviewDetail -> {
                        if (reviewDetail == null) {
                            logger.warn("Review detail not found id={}", req.getReviewDetailId());
                            throw new ResourceNotFoundException(
                                    "Review detail not found with id=" + req.getReviewDetailId());
                        }

                        return Uni.createFrom().item(() -> {
                            reviewDetail.setType(req.getType());
                            reviewDetail.setUrl(req.getFile());
                            reviewDetail.setCaption(req.getCaption());
                            reviewDetail.setUpdatedAt(Instant.now());
                            return reviewDetail;
                        }).chain(updated -> reviewDetailRepository.update(updated)
                                .map(ReviewDetailResponse::from));
                    });

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return tracingMetrics.traceAndMeasure("updateReviewDetails", "update_review_details",
                () -> Uni.join().all(unis).andCollectFailures()
                        .map(responses -> {
                            logger.info("Successfully updated {} review details", responses.size());
                            return ApiResponse.success("Review details updated successfully!", responses);
                        })
                        .onFailure().invoke(e -> logger.error("Failed to update review details", e)));
    }

    @Override
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> trash(String reviewDetailId) {
        logger.info("Trashing review detail id={}", reviewDetailId);

        return tracingMetrics.traceAndMeasure("trashReviewDetail", "trash_review_detail",
                Attributes.builder().put("review.detail.id", reviewDetailId).build(),
                () -> reviewDetailRepository.trash(reviewDetailId)
                        .map(trashed -> {
                            if (trashed == null) {
                                logger.warn("Failed to trash review detail - not found or already trashed with ID: {}",
                                        reviewDetailId);
                                throw new ResourceNotFoundException("Review detail not found or already trashed");
                            }
                            logger.info("Successfully trashed review detail with ID: {}", reviewDetailId);
                            return ApiResponse.success("Review detail trashed successfully!",
                                    ReviewDetailResponseDeleteAt.from(trashed));
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to trash review detail id={}", reviewDetailId, e)));
    }

    @Override
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> restore(String reviewDetailId) {
        logger.info("Restoring review detail id={}", reviewDetailId);

        return tracingMetrics.traceAndMeasure("restoreReviewDetail", "restore_review_detail",
                Attributes.builder().put("review.detail.id", reviewDetailId).build(),
                () -> reviewDetailRepository.restore(reviewDetailId)
                        .map(restored -> {
                            if (restored == null) {
                                logger.warn("Failed to restore review detail - not found or not trashed with ID: {}",
                                        reviewDetailId);
                                throw new ResourceNotFoundException("Review detail not found or not trashed");
                            }
                            logger.info("Successfully restored review detail with ID: {}", reviewDetailId);
                            return ApiResponse.success("Review detail restored successfully!",
                                    ReviewDetailResponseDeleteAt.from(restored));
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to restore review detail id={}", reviewDetailId, e)));
    }

    @Override
    public Uni<ApiResponse<Void>> delete(String reviewDetailId) {
        Attributes attrs = Attributes.builder().put("review.detail.id", reviewDetailId).build();
        logger.warn("Permanently deleting review detail id={}", reviewDetailId);

        return tracingMetrics.traceAndMeasure("deleteReviewDetail", "delete_review_detail_permanent", attrs, () -> {
            return reviewDetailRepository.deletePermanent(reviewDetailId)
                    .map(deleted -> {
                        if (deleted == null) {
                            logger.warn("Permanent delete failed - review detail not found or must be trashed before permanent deletion with id: {}",
                                    reviewDetailId);
                            throw new InvalidRequestException("Review detail not found or must be trashed before permanent deletion");
                        }
                        logger.info("Successfully permanently deleted review detail with ID: {}", reviewDetailId);
                        return ApiResponse.success("Review detail permanently deleted");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> restoreAll() {
        logger.info("Restoring ALL trashed review details");

        return tracingMetrics.traceAndMeasure("restoreAllReviewDetails", "restore_all_review_details", () -> {
            return reviewDetailRepository.restoreAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed review details found");
                        }
                        logger.info("Successfully restored all trashed review details");
                        return ApiResponse.success("All review details restored successfully");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> deleteAll() {
        logger.warn("Permanently deleting ALL trashed review details");

        return tracingMetrics.traceAndMeasure("deleteAllReviewDetails", "delete_all_review_details_permanent", () -> {
            return reviewDetailRepository.deleteAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed review details found");
                        }
                        logger.info("Successfully permanently deleted all trashed review details");
                        return ApiResponse.success("All review details permanently deleted");
                    });
        });
    }

    @Override
    public Uni<ApiResponsePagination<List<ReviewDetailResponse>>> findAll(int page, int size, String search) {
        logger.info("Finding all review details with page={}, size={}, search={}", page, size, search);

        return tracingMetrics.traceAndMeasure("findAllReviewDetails", "find_all_review_details", () -> {
            boolean hasSearch = search != null && !search.trim().isEmpty();

            ReactivePanacheQuery<ReviewDetail> query;
            if (hasSearch) {
                query = reviewDetailRepository.find(SEARCH_FILTER, Pattern.quote(search.trim()));
            } else {
                query = reviewDetailRepository.findAll();
            }
            query.page(page - 1, size);

            return Uni.combine().all().unis(query.list(), query.count()).asTuple()
                    .map(tuple -> {
                        List<ReviewDetailResponse> list = tuple.getItem1().stream()
                                .map(ReviewDetailResponse::from)
                                .collect(Collectors.toList());
                        long totalCount = tuple.getItem2();
                        int totalPages = (int) ((totalCount + size - 1) / size);
                        PaginationMeta pagination = new PaginationMeta(page, size, totalPages, (int) totalCount);
                        return new ApiResponsePagination<>("success", "Review details retrieved successfully", list,
                                pagination);
                    });
        });
    }

    @Override
    public Uni<ApiResponsePagination<List<ReviewDetailResponseDeleteAt>>> findByActive(int page, int size,
            String search) {
        logger.info("Finding active review details with page={}, size={}, search={}", page, size, search);

        return tracingMetrics.traceAndMeasure("findActiveReviewDetails", "find_active_review_details", () -> {
            boolean hasSearch = search != null && !search.trim().isEmpty();
            String filter = hasSearch ? ACTIVE_SEARCH_FILTER : ACTIVE_FILTER;

            ReactivePanacheQuery<ReviewDetail> query = hasSearch
                    ? reviewDetailRepository.find(filter, Pattern.quote(search.trim()))
                    : reviewDetailRepository.find(filter);
            query.page(page - 1, size);

            return Uni.combine().all().unis(query.list(), query.count()).asTuple()
                    .map(tuple -> {
                        List<ReviewDetailResponseDeleteAt> list = tuple.getItem1().stream()
                                .map(ReviewDetailResponseDeleteAt::from)
                                .collect(Collectors.toList());
                        long totalCount = tuple.getItem2();
                        int totalPages = (int) ((totalCount + size - 1) / size);
                        PaginationMeta pagination = new PaginationMeta(page, size, totalPages, (int) totalCount);
                        return new ApiResponsePagination<>("success", "Active review details retrieved successfully",
                                list, pagination);
                    });
        });
    }

    @Override
    public Uni<ApiResponsePagination<List<ReviewDetailResponseDeleteAt>>> findByTrashed(int page, int size,
            String search) {
        logger.info("Finding trashed review details with page={}, size={}, search={}", page, size, search);

        return tracingMetrics.traceAndMeasure("findTrashedReviewDetails", "find_trashed_review_details", () -> {
            boolean hasSearch = search != null && !search.trim().isEmpty();
            String filter = hasSearch ? TRASHED_SEARCH_FILTER : TRASHED_FILTER;

            ReactivePanacheQuery<ReviewDetail> query = hasSearch
                    ? reviewDetailRepository.find(filter, Pattern.quote(search.trim()))
                    : reviewDetailRepository.find(filter);
            query.page(page - 1, size);

            return Uni.combine().all().unis(query.list(), query.count()).asTuple()
                    .map(tuple -> {
                        List<ReviewDetailResponseDeleteAt> list = tuple.getItem1().stream()
                                .map(ReviewDetailResponseDeleteAt::from)
                                .collect(Collectors.toList());
                        long totalCount = tuple.getItem2();
                        int totalPages = (int) ((totalCount + size - 1) / size);
                        PaginationMeta pagination = new PaginationMeta(page, size, totalPages, (int) totalCount);
                        return new ApiResponsePagination<>("success", "Trashed review details retrieved successfully",
                                list, pagination);
                    });
        });
    }

    @Override
    public Uni<ApiResponse<ReviewDetailResponse>> findById(String id) {
        logger.info("Finding review detail by id={}", id);

        return tracingMetrics.traceAndMeasure("findReviewDetailById", "find_review_detail_by_id",
                Attributes.builder().put("review.detail.id", id).build(),
                () -> reviewDetailRepository.findById(id)
                        .map(detail -> {
                            if (detail == null) {
                                logger.warn("Review detail not found with id={}", id);
                                throw new ResourceNotFoundException("Review detail not found with id=" + id);
                            }
                            return ApiResponse.success("Review detail retrieved successfully",
                                    ReviewDetailResponse.from(detail));
                        })
                        .onFailure().invoke(e -> logger.error("Error finding review detail by ID: {}", id, e)));
    }

    private static final String SEARCH_CLAUSE = "'$or': [ "
            + "{ 'caption': { '$regex': ?1, '$options': 'i' } }, "
            + "{ 'url': { '$regex': ?1, '$options': 'i' } } ]";

    private static final String SEARCH_FILTER = "{ " + SEARCH_CLAUSE + " }";
    private static final String ACTIVE_FILTER = "{ 'deletedAt': null }";
    private static final String ACTIVE_SEARCH_FILTER = "{ 'deletedAt': null, " + SEARCH_CLAUSE + " }";
    private static final String TRASHED_FILTER = "{ 'deletedAt': { '$ne': null } }";
    private static final String TRASHED_SEARCH_FILTER = "{ 'deletedAt': { '$ne': null }, " + SEARCH_CLAUSE + " }";
}
