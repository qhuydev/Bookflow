package com.bookflow.businesses.application;

import com.bookflow.businesses.api.UpdateBusinessRequest;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.repository.BusinessConfigurationRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Profile("!test")
public class BusinessConfigurationService {
    private final TenantAuthorizationService authorization;
    private final BusinessUpdateRequestValidator validator;
    private final BusinessConfigurationRepository repository;

    public BusinessConfigurationService(TenantAuthorizationService authorization, BusinessUpdateRequestValidator validator,
                                        BusinessConfigurationRepository repository) {
        this.authorization = authorization;
        this.validator = validator;
        this.repository = repository;
    }

    @Transactional
    public BusinessMembershipView update(UUID userId, UUID businessId, UpdateBusinessRequest request) {
        BusinessMembershipView membership = authorization.requirePermission(userId, businessId, TenantPermission.BUSINESS_CONFIGURATION_MANAGE);
        var update = validator.validate(request);
        Business business = repository.updateActiveBusiness(businessId, userId, update).orElseThrow(ResourceNotFoundException::new);
        return new BusinessMembershipView(business, membership.membershipRole(), membership.membershipStatus());
    }
}
