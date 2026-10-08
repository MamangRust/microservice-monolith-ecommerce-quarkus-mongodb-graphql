package com.sanedge.common.adapter.model;

import java.time.Instant;

/** Domain model order item (setara {@code models.OrderItem} di Go). */
public record OrderItem(
        String id,
        String orderId,
        String productId,
        int quantity,
        int price,
        Instant createdAt,
        Instant updatedAt) {
}
