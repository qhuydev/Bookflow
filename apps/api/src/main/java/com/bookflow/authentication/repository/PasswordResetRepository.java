package com.bookflow.authentication.repository;

import com.bookflow.authentication.domain.StoredPasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRepository {
    Optional<UUID> findUserIdByNormalizedEmail(String normalizedEmail);
    void revokeActiveTokens(UUID userId, Instant now, String reason);
    void createToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant now);
    StoredPasswordResetToken lockByHash(String tokenHash);
    void resetPasswordAndRevokeAuthentication(
            StoredPasswordResetToken token,
            String passwordHash,
            Instant now
    );
}
