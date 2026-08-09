package com.bookflow.businesses.api;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
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
        String currencyCode,
        String cancellationPolicy,
        int maxBookingAdvanceDays,
        String status,
        MembershipResponse membership,
        Instant createdAt
) {
    public static BusinessResponse ownerBusiness(Business business) {
        return from(new BusinessMembershipView(business, MembershipRole.OWNER, MembershipStatus.ACTIVE));
    }

    public static BusinessResponse from(BusinessMembershipView view) {
        Business business = view.business();
        return new BusinessResponse(
                business.id(),
                business.name(),
                business.slug(),
                business.businessType().name(),
                business.timeZone(),
                business.currencyCode(),
                business.cancellationPolicy().name(),
                business.maxBookingAdvanceDays(),
                business.status().name(),
                new MembershipResponse(view.membershipRole().name(), view.membershipStatus().name()),
                business.createdAt()
        );
    }

    public record MembershipResponse(String role, String status) {
    }
}
