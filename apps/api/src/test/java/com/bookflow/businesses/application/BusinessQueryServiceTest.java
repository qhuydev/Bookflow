package com.bookflow.businesses.application;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessQueryServiceTest {
    private final TenantAuthorizationService authorization = mock(TenantAuthorizationService.class);
    private final BusinessQueryService service = new BusinessQueryService(authorization);

    @Test
    void returnsOnlyRepositoryResultsForActiveCurrentUser() {
        UUID userId = UUID.randomUUID();
        BusinessMembershipView view = view();
        when(authorization.listVisibleBusinesses(userId)).thenReturn(List.of(view));

        assertThat(service.listForCurrentUser(userId)).containsExactly(view);
    }

    @Test
    void detailDelegatesBusinessViewPermissionToSharedAuthorization() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        BusinessMembershipView view = view();
        when(authorization.requirePermission(userId, businessId, TenantPermission.BUSINESS_VIEW)).thenReturn(view);

        assertThat(service.getForCurrentUser(userId, businessId)).isEqualTo(view);
    }

    @Test
    void authorizationErrorIsNotChangedByQueryService() {
        UUID userId = UUID.randomUUID();
        when(authorization.listVisibleBusinesses(userId))
                .thenThrow(new CurrentBusinessUserUnavailableException());

        assertThatThrownBy(() -> service.listForCurrentUser(userId))
                .isInstanceOf(CurrentBusinessUserUnavailableException.class);
    }

    private BusinessMembershipView view() {
        Instant now = Instant.now();
        return new BusinessMembershipView(new Business(UUID.randomUUID(), "Name", "name", BusinessType.SALON,
                "UTC", BusinessStatus.ACTIVE, now, now), MembershipRole.STAFF, MembershipStatus.ACTIVE);
    }
}
