package com.bookflow.businesses.authorization;

import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.repository.BusinessQueryRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves current membership from PostgreSQL on every tenant-scoped request. */
@Service
@Profile("!test")
public class TenantAuthorizationService {
    private static final Map<TenantPermission, EnumSet<MembershipRole>> PERMISSIONS = Map.of(
            TenantPermission.BUSINESS_VIEW, EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN, MembershipRole.STAFF),
            TenantPermission.BUSINESS_CONFIGURATION_MANAGE, EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN),
            TenantPermission.MEMBERSHIP_STAFF_MANAGE, EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN),
            TenantPermission.MEMBERSHIP_PRIVILEGED_MANAGE, EnumSet.of(MembershipRole.OWNER),
            TenantPermission.BUSINESS_CLOSE, EnumSet.of(MembershipRole.OWNER)
    );

    private final BusinessQueryRepository repository;

    public TenantAuthorizationService(BusinessQueryRepository repository) {
        this.repository = repository;
    }

    public List<BusinessMembershipView> listVisibleBusinesses(UUID userId) {
        requireActiveUser(userId);
        return repository.findActiveBusinessesForUser(userId);
    }

    public BusinessMembershipView requirePermission(UUID userId, UUID tenantId, TenantPermission permission) {
        requireActiveUser(userId);
        BusinessMembershipView membership = repository.findActiveBusinessForUser(userId, tenantId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!PERMISSIONS.get(permission).contains(membership.membershipRole())) {
            throw new TenantPermissionDeniedException();
        }
        return membership;
    }

    private void requireActiveUser(UUID userId) {
        if (!repository.hasActiveUser(userId)) {
            throw new CurrentBusinessUserUnavailableException();
        }
    }
}
