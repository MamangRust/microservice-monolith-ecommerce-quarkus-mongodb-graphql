package com.sanedge.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.entity.Review;
import com.sanedge.review.repository.ReviewQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceImplTest {

    private static final String ID_1 = "507f1f77bcf86cd799439011";
    private static final String ID_999 = "507f1f77bcf86cd799439099";

    @Mock
    private ReviewQueryRepository reviewQueryRepository;
    @Mock
    private RedisService redisService;
    @Mock
    private TracingMetrics tracingMetrics;

    private ReviewQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        service = new ReviewQueryServiceImpl(
                reviewQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);
    }

    private Review mkReview(String id) {
        Review r = new Review();
        r.id = new ObjectId(id);
        r.setComment("Great");
        r.setUserId(new ObjectId("507f1f77bcf86cd799439021"));
        r.setProductId(new ObjectId("507f1f77bcf86cd799439031"));
        r.setRating(5);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return r;
    }

    @Test
    void findById_Success() {
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviewById(anyString()))
                .thenReturn(Uni.createFrom().item(Optional.of(mkReview(ID_1))));
        ApiResponse<?> result = service.findById(ID_1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Review retrieved successfully");
    }

    @Test
    void findById_NotFound_ThrowsResourceNotFound() {
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviewById(anyString())).thenReturn(Uni.createFrom().item(Optional.empty()));
        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> service.findById(ID_999).await().indefinitely());
    }

    @Test
    void findById_CacheHit_ReturnsCached() {
        Review review = mkReview(ID_1);
        Object cached = com.sanedge.review.domain.response.ReviewResponse.from(review);
        String cachedJson;
        try {
            cachedJson = objectMapper.writeValueAsString(cached);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(cachedJson));
        ApiResponse<?> result = service.findById(ID_1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Review retrieved successfully");
    }

    @Test
    void findAll_Success() {
        FindAllReview req = new FindAllReview();
        req.setPage(1);
        req.setPageSize(10);
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviews(any(FindAllReview.class)))
                .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(), 0)));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());
        var result = service.findAll(req).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    /**
     * Finds the Supplier argument in the invocation regardless of whether it was
     * passed positionally in the 3-arg overload (arg index 2) or 4-arg overload
     * (arg index 3), then invokes it and returns the resulting Uni. This lets
     * a single Answer<?> body serve both traceAndMeasure overloads.
     */
    private Answer<Uni<?>> invokeSupplier() {
        return invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? (Uni<?>) supplier.get() : null;
        };
    }
}
