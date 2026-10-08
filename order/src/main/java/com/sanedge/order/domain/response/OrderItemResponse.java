package com.sanedge.order.domain.response;

import com.sanedge.common.adapter.model.OrderItem;

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
        if (item == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(item.id())
                .orderId(item.orderId())
                .productId(item.productId())
                .quantity(item.quantity())
                .price(item.price())
                .createdAt(item.createdAt() != null ? item.createdAt().toString() : null)
                .updatedAt(item.updatedAt() != null ? item.updatedAt().toString() : null)
                .build();
    }
}
