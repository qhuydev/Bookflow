package com.bookflow.bookings.domain;

import java.time.Instant;
import java.util.UUID;

public record BookingStatusHistory(
        UUID id,
        UUID tenantId,
        UUID bookingId,
        BookingStatus fromStatus,
        BookingStatus toStatus,
        UUID actorUserId,
        String reason,
        Instant changedAt
) {
}
