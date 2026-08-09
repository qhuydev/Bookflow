package com.bookflow.businesses.application;

import com.bookflow.businesses.api.CreateBusinessRequest;
import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.repository.BusinessCreationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Profile("!test")
public class BusinessCreationService {

    private final BusinessCreationRequestValidator validator;
    private final BusinessCreationRepository repository;

    public BusinessCreationService(
            BusinessCreationRequestValidator validator,
            BusinessCreationRepository repository
    ) {
        this.validator = validator;
        this.repository = repository;
    }

    @Transactional
    public Business create(UUID authenticatedUserId, CreateBusinessRequest request) {
        if (!repository.hasActiveUser(authenticatedUserId)) {
            throw new CurrentBusinessUserUnavailableException();
        }
        var validated = validator.validate(request);
        UUID businessId = UUID.randomUUID();
        Business business = repository.insertBusiness(
                businessId,
                validated.name(),
                validated.slug(),
                validated.businessType(),
                validated.timeZone(),
                BusinessStatus.ACTIVE
        );
        repository.insertInitialOwnerMembership(business.id(), authenticatedUserId);
        return business;
    }
}
