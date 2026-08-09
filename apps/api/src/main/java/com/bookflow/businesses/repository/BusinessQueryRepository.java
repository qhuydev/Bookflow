package com.bookflow.businesses.repository;

import com.bookflow.businesses.domain.BusinessMembershipView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessQueryRepository {
    boolean hasActiveUser(UUID userId);
    List<BusinessMembershipView> findActiveBusinessesForUser(UUID userId);
    Optional<BusinessMembershipView> findActiveBusinessForUser(UUID userId, UUID businessId);
}
