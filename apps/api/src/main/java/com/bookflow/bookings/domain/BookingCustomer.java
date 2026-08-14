package com.bookflow.bookings.domain;

import java.util.UUID;

public record BookingCustomer(UUID userId, String name, String email, String phone) {
    public BookingCustomer {
        name = normalizeRequired(name, "Customer name is required.");
        email = normalizeOptional(email);
        phone = normalizeOptional(phone);
        if (userId == null && email == null && phone == null) {
            throw new InvalidBookingException("A registered user or guest contact is required.");
        }
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidBookingException(message);
        }
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
