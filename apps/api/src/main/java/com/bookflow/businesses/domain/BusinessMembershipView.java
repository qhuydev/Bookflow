package com.bookflow.businesses.domain;

/** Business visible to one authenticated user together with only that user's membership. */
public record BusinessMembershipView(
        Business business,
        MembershipRole membershipRole,
        MembershipStatus membershipStatus
) {
}
