package com.bookflow.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record StoredPasswordResetToken(UUID id, UUID userId, String status, Instant expiresAt) { }
