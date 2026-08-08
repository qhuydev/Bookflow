package com.bookflow.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record NewUser(
        UUID id,
        String normalizedEmail,
        String passwordHash,
        Instant registeredAt
) {
}
