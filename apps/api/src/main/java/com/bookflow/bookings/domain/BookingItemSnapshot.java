package com.bookflow.bookings.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public record BookingItemSnapshot(
        UUID serviceId,
        String serviceName,
        BigDecimal price,
        String currency,
        int durationMinutes,
        int bufferBeforeMinutes,
        int bufferAfterMinutes
) {
    public BookingItemSnapshot {
        if (serviceId == null) {
            throw new InvalidBookingException("Service id is required.");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new InvalidBookingException("Service snapshot name is required.");
        }
        serviceName = serviceName.strip();
        if (price == null || price.signum() < 0) {
            throw new InvalidBookingException("Service snapshot price cannot be negative.");
        }
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new InvalidBookingException("Service snapshot currency must contain three letters.");
        }
        currency = currency.toUpperCase(Locale.ROOT);
        if (durationMinutes <= 0) {
            throw new InvalidBookingException("Service snapshot duration must be positive.");
        }
        if (bufferBeforeMinutes < 0 || bufferAfterMinutes < 0) {
            throw new InvalidBookingException("Service snapshot buffers cannot be negative.");
        }
    }
}
