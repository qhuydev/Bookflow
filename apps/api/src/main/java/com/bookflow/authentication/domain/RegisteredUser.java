package com.bookflow.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record RegisteredUser(
        UUID id,
        String email,
        UserStatus status,
        Instant createdAt
) {
}
