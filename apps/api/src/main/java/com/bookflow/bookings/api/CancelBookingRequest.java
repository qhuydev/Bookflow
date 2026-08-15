package com.bookflow.bookings.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CancelBookingRequest {
    private String reason;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }

    @JsonAnySetter
    void captureUnknownField(String field, Object ignored) {
        unknownFields.add(field);
    }
}
