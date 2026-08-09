package com.bookflow.businesses.application;

import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Profile("!test")
public class BusinessQueryService {
    private final TenantAuthorizationService tenantAuthorization;

    public BusinessQueryService(TenantAuthorizationService tenantAuthorization) {
        this.tenantAuthorization = tenantAuthorization;
    }

    public List<BusinessMembershipView> listForCurrentUser(UUID userId) {
        return tenantAuthorization.listVisibleBusinesses(userId);
    }

    public BusinessMembershipView getForCurrentUser(UUID userId, UUID businessId) {
        return tenantAuthorization.requirePermission(userId, businessId, TenantPermission.BUSINESS_VIEW);
    }
}
