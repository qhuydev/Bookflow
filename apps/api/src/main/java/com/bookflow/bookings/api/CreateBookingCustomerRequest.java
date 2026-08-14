package com.bookflow.bookings.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CreateBookingCustomerRequest {
    private String name;
    private String email;
    private String phone;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }

    @JsonAnySetter
    void captureUnknownField(String field, Object ignored) {
        unknownFields.add(field);
    }
}
