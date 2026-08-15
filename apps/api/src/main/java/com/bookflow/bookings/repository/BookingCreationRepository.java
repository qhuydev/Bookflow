package com.bookflow.bookings.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingCreationRepository {
    Optional<CreationContext> findCreationContext(
            String slug,
            UUID branchId,
            UUID serviceId
    );

    List<UUID> findEligibleEmployees(
            UUID tenantId,
            UUID branchId,
            UUID serviceId,
            UUID requestedEmployeeId,
            Instant now
    );

    boolean claimIdempotencyKey(
            UUID id,
            UUID tenantId,
            String key,
            String fingerprint,
            Instant createdAt
    );

    Optional<IdempotencyRecord> findIdempotencyKeyForUpdate(UUID tenantId, String key);

    void completeIdempotencyKey(
            UUID tenantId,
            String key,
            UUID bookingId,
            Instant completedAt
    );

    record CreationContext(
            UUID tenantId,
            UUID branchId,
            UUID serviceId,
            ZoneId zoneId,
            String serviceName,
            BigDecimal price,
            String currency,
            int durationMinutes,
            int bufferBeforeMinutes,
            int bufferAfterMinutes
    ) {
    }

    record IdempotencyRecord(String fingerprint, UUID bookingId) {
    }
}
