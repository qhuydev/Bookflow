package com.bookflow.bookings.application;

import com.bookflow.bookings.application.BookingRequestValidator.ValidatedBookingRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public final class BookingRequestFingerprint {
    public String fingerprint(ValidatedBookingRequest request) {
        String canonical = String.join("|",
                field(request.slug()),
                field(request.branchId().toString()),
                field(request.serviceId().toString()),
                field(request.employeeId() == null ? null : request.employeeId().toString()),
                field(request.start().toInstant().toString()),
                field(request.customer().name()),
                field(request.customer().email()),
                field(request.customer().phone())
        );
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String field(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }
}
