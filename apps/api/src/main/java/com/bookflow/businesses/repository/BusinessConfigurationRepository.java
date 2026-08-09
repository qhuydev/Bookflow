package com.bookflow.businesses.repository;

import com.bookflow.businesses.application.BusinessUpdateRequestValidator.ValidatedBusinessUpdate;
import com.bookflow.businesses.domain.Business;

import java.util.Optional;
import java.util.UUID;

public interface BusinessConfigurationRepository {
    Optional<Business> updateActiveBusiness(UUID businessId, UUID userId, ValidatedBusinessUpdate update);
}
