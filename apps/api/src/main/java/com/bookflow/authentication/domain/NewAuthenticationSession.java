package com.bookflow.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record NewAuthenticationSession(UUID id, UUID userId, Instant issuedAt, Instant inactivityExpiresAt, Instant absoluteExpiresAt) { }
