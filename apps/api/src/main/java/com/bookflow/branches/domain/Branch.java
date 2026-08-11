package com.bookflow.branches.domain;

import java.time.Instant;
import java.util.UUID;

public record Branch(
        UUID id, UUID tenantId, String code, String name,
        String addressLine1, String addressLine2, String ward, String district,
        String city, String postalCode, String countryCode, String phone, String email,
        String timeZone, BranchStatus status, Instant createdAt, Instant updatedAt
) { }
