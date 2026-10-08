package com.sanedge.common.adapter.model;

import java.time.Instant;

/** Domain model transaction (setara {@code models.Transaction} di Go). */
public record Transaction(
        String id,
        String orderId,
        String merchantId,
        String paymentMethod,
        int amount,
        String paymentStatus,
        Instant createdAt,
        Instant updatedAt) {
}
