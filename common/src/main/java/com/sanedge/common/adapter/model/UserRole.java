package com.sanedge.common.adapter.model;

import java.time.Instant;

/** Domain model user-role (setara {@code models.UserRole} di Go). */
public record UserRole(
        String userRoleId,
        String userId,
        String roleId,
        Instant createdAt,
        Instant updatedAt) {
}
