package com.bookflow.businesses.repository;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;

import java.util.UUID;

public interface BusinessCreationRepository {
    boolean hasActiveUser(UUID userId);

    Business insertBusiness(
            UUID id,
            String name,
            String slug,
            BusinessType businessType,
            String timeZone,
            BusinessStatus status
    );

    void insertInitialOwnerMembership(UUID tenantId, UUID userId);
}
