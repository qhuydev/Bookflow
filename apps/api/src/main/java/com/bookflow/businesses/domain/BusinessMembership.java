package com.bookflow.businesses.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a global user to a business tenant. tenantId is businesses.id per ADR 0002.
 */
public record BusinessMembership(
        UUID id,
        UUID tenantId,
        UUID userId,
        MembershipRole role,
        MembershipStatus status,
        Instant revokedAt,
        UUID revokedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
