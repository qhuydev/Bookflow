package com.bookflow.authentication.repository;

import com.bookflow.authentication.domain.LoginUser;
import com.bookflow.authentication.domain.NewAuthenticationSession;
import com.bookflow.authentication.domain.NewRefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.bookflow.authentication.domain.StoredRefreshToken;
import com.bookflow.authentication.domain.RotatedRefreshToken;

public interface UserAuthenticationRepository {
    Optional<LoginUser> findByNormalizedEmail(String normalizedEmail);
    void persistSuccessfulLogin(UUID userId, Instant loginAt, NewAuthenticationSession session, NewRefreshToken refreshToken);
    StoredRefreshToken lockRefreshToken(String hash);
    void rotateRefreshToken(StoredRefreshToken current, RotatedRefreshToken replacement, Instant now, Instant inactivityExpiry, Instant absoluteExpiry);
    void revokeFamily(UUID familyId, Instant now, String reason);
    void revokeAllForUser(UUID userId, Instant now, String reason);
}
