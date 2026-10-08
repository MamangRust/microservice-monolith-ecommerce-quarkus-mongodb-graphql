package com.sanedge.order.domain.response;

import java.util.List;

import com.sanedge.order.entity.Order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRelationResponse {
    private String orderId;
    private String userId;
    private String merchantId;
    private Integer totalPrice;
    private List<OrderItemResponse> orderItems;

    public static OrderRelationResponse from(Order order, List<OrderItemResponse> orderItems) {
        if (order == null) {
            return null;
        }
        return OrderRelationResponse.builder()
                .orderId(order.id != null ? order.id.toHexString() : null)
                .userId(order.getUserId() != null ? order.getUserId().toHexString() : null)
                .merchantId(order.getMerchantId() != null ? order.getMerchantId().toHexString() : null)
                .totalPrice(order.getTotalPrice())
                .orderItems(orderItems != null ? orderItems : List.of())
                .build();
    }
}
