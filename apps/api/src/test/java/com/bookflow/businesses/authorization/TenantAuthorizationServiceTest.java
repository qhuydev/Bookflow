package com.bookflow.businesses.authorization;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.CancellationPolicy;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;
import com.bookflow.businesses.repository.BusinessQueryRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantAuthorizationServiceTest {
    private final BusinessQueryRepository repository = mock(BusinessQueryRepository.class);
    private final TenantAuthorizationService service = new TenantAuthorizationService(repository);

    @Test
    void permissionMatrixUsesExplicitRolesRatherThanRoleOrdering() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        assertAllowed(userId, businessId, MembershipRole.OWNER, TenantPermission.BUSINESS_CLOSE);
        assertAllowed(userId, businessId, MembershipRole.ADMIN, TenantPermission.BUSINESS_CONFIGURATION_MANAGE);
        assertAllowed(userId, businessId, MembershipRole.STAFF, TenantPermission.BUSINESS_VIEW);
        assertDenied(userId, businessId, MembershipRole.ADMIN, TenantPermission.BUSINESS_CLOSE);
        assertDenied(userId, businessId, MembershipRole.STAFF, TenantPermission.BUSINESS_CONFIGURATION_MANAGE);
    }

    @Test
    void noActiveMembershipLooksLikeNotFoundBeforePermissionCheck() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        when(repository.hasActiveUser(userId)).thenReturn(true);
        when(repository.findActiveBusinessForUser(userId, businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requirePermission(userId, businessId, TenantPermission.BUSINESS_VIEW))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void assertAllowed(UUID userId, UUID businessId, MembershipRole role, TenantPermission permission) {
        when(repository.hasActiveUser(userId)).thenReturn(true);
        when(repository.findActiveBusinessForUser(userId, businessId)).thenReturn(Optional.of(view(role)));
        assertThat(service.requirePermission(userId, businessId, permission).membershipRole()).isEqualTo(role);
    }

    private void assertDenied(UUID userId, UUID businessId, MembershipRole role, TenantPermission permission) {
        when(repository.hasActiveUser(userId)).thenReturn(true);
        when(repository.findActiveBusinessForUser(userId, businessId)).thenReturn(Optional.of(view(role)));
        assertThatThrownBy(() -> service.requirePermission(userId, businessId, permission))
                .isInstanceOf(TenantPermissionDeniedException.class);
    }

    private BusinessMembershipView view(MembershipRole role) {
        Instant now = Instant.now();
        return new BusinessMembershipView(new Business(UUID.randomUUID(), "Name", "name", BusinessType.SALON,
                "UTC", "VND", CancellationPolicy.FLEXIBLE, 90, BusinessStatus.ACTIVE, now, now), role, MembershipStatus.ACTIVE);
    }
}
