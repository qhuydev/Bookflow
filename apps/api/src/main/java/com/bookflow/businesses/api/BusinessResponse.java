package com.bookflow.businesses.api;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String slug,
        String type,
        String timeZone,
        String status,
        MembershipResponse membership,
        Instant createdAt
) {
    public static BusinessResponse ownerBusiness(Business business) {
        return new BusinessResponse(
                business.id(),
                business.name(),
                business.slug(),
                business.businessType().name(),
                business.timeZone(),
                business.status().name(),
                new MembershipResponse(MembershipRole.OWNER.name(), MembershipStatus.ACTIVE.name()),
                business.createdAt()
        );
    }

    public record MembershipResponse(String role, String status) {
    }
}
