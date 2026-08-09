package com.bookflow.authentication.application;

import com.bookflow.authentication.config.AuthenticationProperties;
import com.bookflow.authentication.notification.PasswordResetNotificationPort;
import com.bookflow.authentication.password.Argon2idPasswordHasher;
import com.bookflow.authentication.repository.PasswordResetRepository;
import com.bookflow.authentication.token.PasswordResetTokenGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Profile("!test")
public class PasswordRecoveryService {
    private final PasswordRecoveryRequestValidator validator;
    private final PasswordResetRepository repository;
    private final PasswordResetTokenGenerator tokens;
    private final PasswordResetNotificationPort notifications;
    private final Argon2idPasswordHasher passwords;
    private final AuthenticationProperties properties;
    private final Clock clock;

    public PasswordRecoveryService(
            PasswordRecoveryRequestValidator validator,
            PasswordResetRepository repository,
            PasswordResetTokenGenerator tokens,
            PasswordResetNotificationPort notifications,
            Argon2idPasswordHasher passwords,
            AuthenticationProperties properties,
            Clock clock
    ) {
        this.validator = validator;
        this.repository = repository;
        this.tokens = tokens;
        this.notifications = notifications;
        this.passwords = passwords;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void forgotPassword(String email) {
        validator.validateForgot(email);
        String normalizedEmail = EmailNormalizer.normalize(email);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.passwordReset().ttlMinutes(), ChronoUnit.MINUTES);
        var generated = tokens.generate();
        repository.findUserIdByNormalizedEmail(normalizedEmail).ifPresent(userId -> {
            repository.revokeActiveTokens(userId, now, "SUPERSEDED");
            repository.createToken(UUID.randomUUID(), userId, generated.hash(), expiresAt, now);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notifications.sendPasswordReset(normalizedEmail, generated.rawToken(), expiresAt);
                    } catch (RuntimeException ignored) {
                        // The public response must stay generic. A provider adapter may retry out of band.
                    }
                }
            });
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validator.validateReset(rawToken, newPassword);
        Instant now = clock.instant();
        var stored = repository.lockByHash(tokens.sha256(rawToken));
        if (stored == null || !"ACTIVE".equals(stored.status()) || !now.isBefore(stored.expiresAt())) {
            throw new InvalidPasswordResetTokenException();
        }
        repository.resetPasswordAndRevokeAuthentication(stored, passwords.hash(newPassword), now);
    }
}
