package com.bookflow.bookings.domain;

import java.time.Instant;
import java.util.UUID;

public record BookingRescheduleHistory(
        UUID id,
        UUID tenantId,
        UUID bookingId,
        UUID oldEmployeeId,
        UUID newEmployeeId,
        Instant oldStartAt,
        Instant oldEndAt,
        Instant newStartAt,
        Instant newEndAt,
        UUID actorUserId,
        String reason,
        Instant changedAt
) {
}
