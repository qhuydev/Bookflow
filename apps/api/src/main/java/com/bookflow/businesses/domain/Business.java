package com.bookflow.businesses.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable persistence-facing model. It is not an API response or JPA entity.
 */
public record Business(
        UUID id,
        String name,
        String slug,
        BusinessType businessType,
        String timeZone,
        String currencyCode,
        CancellationPolicy cancellationPolicy,
        int maxBookingAdvanceDays,
        BusinessStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
