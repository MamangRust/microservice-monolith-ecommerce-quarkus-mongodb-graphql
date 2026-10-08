package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.ReviewDetailDto.CreateReviewDetailRequest;
import com.sanedge.gateway.dto.ReviewDetailDto.CreateReviewDetailResponse;
import com.sanedge.gateway.dto.ReviewDetailDto.FindAllReviewDetailResponse;
import com.sanedge.gateway.dto.ReviewDetailDto.FindByIdReviewDetailResponse;
import com.sanedge.gateway.dto.ReviewDetailDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.ReviewDetailDto.UpdateReviewDetailRequest;
import com.sanedge.gateway.dto.ReviewDetailDto.UpdateReviewDetailResponse;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public interface ReviewDetailService {
    Uni<FindAllReviewDetailResponse> listReviewDetails(int page, int size, String search);
    Uni<FindAllReviewDetailResponse> listActiveReviewDetails(int page, int size, String search);
    Uni<FindAllReviewDetailResponse> listTrashedReviewDetails(int page, int size, String search);
    Uni<FindByIdReviewDetailResponse> getReviewDetail(String id);
    Uni<CreateReviewDetailResponse> createReviewDetail(CreateReviewDetailRequest body);
    Uni<UpdateReviewDetailResponse> updateReviewDetail(String id, UpdateReviewDetailRequest body);
    Uni<UpdateReviewDetailResponse> uploadReviewDetail(String id, FileUpload file);
    Uni<FindByIdReviewDetailResponse> deleteReviewDetail(String id);
    Uni<FindByIdReviewDetailResponse> restoreReviewDetail(String id);
    Uni<SimpleStatusMessageResponse> deleteReviewDetailPermanent(String id);
    Uni<SimpleStatusMessageResponse> restoreAllReviewDetails();
    Uni<SimpleStatusMessageResponse> deleteAllReviewDetailsPermanent();
}
