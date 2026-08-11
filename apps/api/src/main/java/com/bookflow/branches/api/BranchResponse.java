package com.bookflow.branches.api;

import com.bookflow.branches.domain.Branch;
import java.time.Instant;
import java.util.UUID;

public record BranchResponse(UUID id, String code, String name, String addressLine1, String addressLine2,
                             String ward, String district, String city, String postalCode, String countryCode,
                             String phone, String email, String timeZone, String status, Instant createdAt) {
    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.id(), branch.code(), branch.name(), branch.addressLine1(), branch.addressLine2(),
                branch.ward(), branch.district(), branch.city(), branch.postalCode(), branch.countryCode(), branch.phone(),
                branch.email(), branch.timeZone(), branch.status().name(), branch.createdAt());
    }
}
