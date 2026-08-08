package com.bookflow.authentication.application;

import com.bookflow.authentication.domain.NewUser;
import com.bookflow.authentication.domain.RegisteredUser;
import com.bookflow.authentication.domain.UserStatus;
import com.bookflow.authentication.password.Argon2idPasswordHasher;
import com.bookflow.authentication.repository.UserRegistrationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Profile("!test")
public class UserRegistrationService {

    private final RegistrationRequestValidator requestValidator;
    private final Argon2idPasswordHasher passwordHasher;
    private final UserRegistrationRepository userRegistrationRepository;

    public UserRegistrationService(
            RegistrationRequestValidator requestValidator,
            Argon2idPasswordHasher passwordHasher,
            UserRegistrationRepository userRegistrationRepository
    ) {
        this.requestValidator = requestValidator;
        this.passwordHasher = passwordHasher;
        this.userRegistrationRepository = userRegistrationRepository;
    }

    @Transactional
    public RegisteredUser register(String email, String password) {
        requestValidator.validate(email, password);

        String normalizedEmail = EmailNormalizer.normalize(email);
        Instant registeredAt = Instant.now();
        NewUser newUser = new NewUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHasher.hash(password),
                registeredAt
        );
        userRegistrationRepository.insert(newUser);

        return new RegisteredUser(
                newUser.id(),
                newUser.normalizedEmail(),
                UserStatus.ACTIVE,
                newUser.registeredAt()
        );
    }
}
