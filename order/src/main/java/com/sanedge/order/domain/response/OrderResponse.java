package com.sanedge.order.domain.response;

import com.sanedge.order.entity.Order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String merchantId;
    private String userId;
    private Integer totalPrice;
    private String createdAt;
    private String updatedAt;

    public static OrderResponse from(Order entity) {
        if (entity == null) {
            return null;
        }
        return OrderResponse.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .merchantId(entity.getMerchantId() != null ? entity.getMerchantId().toHexString() : null)
                .userId(entity.getUserId() != null ? entity.getUserId().toHexString() : null)
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}
