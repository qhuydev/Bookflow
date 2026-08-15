package com.bookflow.bookings.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CreateBookingRequest {
    private UUID branchId;
    private UUID serviceId;
    @Schema(description = "Nhân viên cụ thể; bỏ trống để BookFlow tự chọn nhân viên phù hợp")
    private UUID employeeId;
    private OffsetDateTime start;
    private CreateBookingCustomerRequest customer;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public OffsetDateTime getStart() {
        return start;
    }

    public void setStart(OffsetDateTime start) {
        this.start = start;
    }

    public CreateBookingCustomerRequest getCustomer() {
        return customer;
    }

    public void setCustomer(CreateBookingCustomerRequest customer) {
        this.customer = customer;
    }

    public Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }

    @JsonAnySetter
    void captureUnknownField(String field, Object ignored) {
        unknownFields.add(field);
    }
}
