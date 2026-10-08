package com.sanedge.common.adapter.model;

import java.time.Instant;

/** Domain model role (setara {@code models.Role} di Go). */
public record Role(
        String id,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
