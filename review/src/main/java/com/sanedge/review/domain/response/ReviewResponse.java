package com.sanedge.review.domain.response;

import com.sanedge.review.entity.Review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private String id;
    private String userId;
    private String productId;
    private String name;
    private String comment;
    private Integer rating;
    private String createdAt;
    private String updatedAt;

    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
                .id(review.id != null ? review.id.toHexString() : null)
                .userId(review.getUserId() != null ? review.getUserId().toHexString() : null)
                .productId(review.getProductId() != null ? review.getProductId().toHexString() : null)
                .name(review.getName())
                .comment(review.getComment())
                .rating(review.getRating())
                .createdAt(review.getCreatedAt() != null ? review.getCreatedAt().toString() : null)
                .updatedAt(review.getUpdatedAt() != null ? review.getUpdatedAt().toString() : null)
                .build();
    }
}
