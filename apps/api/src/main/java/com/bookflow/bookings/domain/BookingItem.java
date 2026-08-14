package com.bookflow.bookings.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingItem(
        UUID id,
        UUID tenantId,
        UUID bookingId,
        UUID serviceId,
        int position,
        String serviceNameSnapshot,
        BigDecimal priceSnapshot,
        String currencySnapshot,
        int durationMinutesSnapshot,
        int bufferBeforeMinutesSnapshot,
        int bufferAfterMinutesSnapshot,
        Instant createdAt
) {
}
