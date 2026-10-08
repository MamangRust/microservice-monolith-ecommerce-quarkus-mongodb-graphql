package com.sanedge.common.adapter.model;

import java.time.Instant;

/** Domain model order (setara {@code models.Order} di Go). */
public record Order(
        String id,
        String merchantId,
        String userId,
        int totalPrice,
        Instant createdAt,
        Instant updatedAt) {
}
