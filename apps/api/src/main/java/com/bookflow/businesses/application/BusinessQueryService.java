package com.bookflow.businesses.application;

import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.repository.BusinessQueryRepository;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Profile("!test")
public class BusinessQueryService {
    private final BusinessQueryRepository repository;

    public BusinessQueryService(BusinessQueryRepository repository) {
        this.repository = repository;
    }

    public List<BusinessMembershipView> listForCurrentUser(UUID userId) {
        requireActiveUser(userId);
        return repository.findActiveBusinessesForUser(userId);
    }

    public BusinessMembershipView getForCurrentUser(UUID userId, UUID businessId) {
        requireActiveUser(userId);
        return repository.findActiveBusinessForUser(userId, businessId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void requireActiveUser(UUID userId) {
        if (!repository.hasActiveUser(userId)) {
            throw new CurrentBusinessUserUnavailableException();
        }
    }
}
