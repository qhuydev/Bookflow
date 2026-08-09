package com.bookflow.businesses.application;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;
import com.bookflow.businesses.repository.BusinessQueryRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessQueryServiceTest {
    private final BusinessQueryRepository repository = mock(BusinessQueryRepository.class);
    private final BusinessQueryService service = new BusinessQueryService(repository);

    @Test
    void returnsOnlyRepositoryResultsForActiveCurrentUser() {
        UUID userId = UUID.randomUUID();
        BusinessMembershipView view = view();
        when(repository.hasActiveUser(userId)).thenReturn(true);
        when(repository.findActiveBusinessesForUser(userId)).thenReturn(List.of(view));

        assertThat(service.listForCurrentUser(userId)).containsExactly(view);
    }

    @Test
    void missingOrInaccessibleBusinessUsesNeutralNotFound() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        when(repository.hasActiveUser(userId)).thenReturn(true);
        when(repository.findActiveBusinessForUser(userId, businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForCurrentUser(userId, businessId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The requested resource was not found.");
    }

    @Test
    void inactiveOrMissingCurrentUserIsRejectedBeforeQuery() {
        UUID userId = UUID.randomUUID();
        when(repository.hasActiveUser(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.listForCurrentUser(userId))
                .isInstanceOf(CurrentBusinessUserUnavailableException.class);
    }

    private BusinessMembershipView view() {
        Instant now = Instant.now();
        return new BusinessMembershipView(new Business(UUID.randomUUID(), "Name", "name", BusinessType.SALON,
                "UTC", BusinessStatus.ACTIVE, now, now), MembershipRole.STAFF, MembershipStatus.ACTIVE);
    }
}
