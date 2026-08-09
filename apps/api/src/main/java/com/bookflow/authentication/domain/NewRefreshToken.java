package com.bookflow.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record NewRefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant issuedAt, Instant inactivityExpiresAt, Instant absoluteExpiresAt) { }
