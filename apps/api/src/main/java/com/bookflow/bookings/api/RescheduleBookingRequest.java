package com.bookflow.bookings.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class RescheduleBookingRequest {
    private OffsetDateTime start;
    private UUID employeeId;
    private String reason;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public OffsetDateTime getStart() {
        return start;
    }

    public void setStart(OffsetDateTime start) {
        this.start = start;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

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
