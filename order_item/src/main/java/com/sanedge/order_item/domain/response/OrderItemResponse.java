package com.sanedge.order_item.domain.response;

import com.sanedge.order_item.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private String id;
    private String orderId;
    private String productId;
    private Integer quantity;
    private Integer price;
    private String createdAt;
    private String updatedAt;

    public static OrderItemResponse from(OrderItem item) {
        if (item == null) return null;
        return OrderItemResponse.builder()
                .id(item.id != null ? item.id.toHexString() : null)
                .orderId(item.getOrderId() != null ? item.getOrderId().toHexString() : null)
                .productId(item.getProductId() != null ? item.getProductId().toHexString() : null)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null)
                .build();
    }
}
