package com.sanedge.review_detail.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.review_detail.service.ReviewDetailService;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ReviewDetailQueryGrpcHandlerTest {
    @Mock ReviewDetailService reviewDetailService;
    private ReviewDetailQueryGrpcHandler reviewDetailQueryGrpcHandler;

    @BeforeEach
    void setUp() throws Exception {
        reviewDetailQueryGrpcHandler = new ReviewDetailQueryGrpcHandler();
        Field f = ReviewDetailQueryGrpcHandler.class.getDeclaredField("reviewDetailService");
        f.setAccessible(true);
        f.set(reviewDetailQueryGrpcHandler, reviewDetailService);
    }

    @Test
    void findById_Success() {
        lenient().when(reviewDetailService.findById(any(String.class))).thenAnswer(i -> Uni.createFrom().item(ApiResponse.success("ok", null)));
        var result = reviewDetailQueryGrpcHandler.findById(pb.review_detail.ReviewDetailCommon.FindByIdReviewDetailRequest.newBuilder().setId("507f1f77bcf86cd799439011").build()).await().indefinitely();
        assertThat(result).isNotNull();
    }
}
