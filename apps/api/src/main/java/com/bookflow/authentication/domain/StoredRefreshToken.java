package com.bookflow.authentication.domain;
import java.time.Instant;
import java.util.UUID;
public record StoredRefreshToken(UUID id, UUID userId, UUID familyId, String hash, String status, Instant inactivityExpiresAt, Instant absoluteExpiresAt, Instant rotatedAt) { }
