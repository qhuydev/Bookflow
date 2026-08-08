package com.bookflow.authentication.api;

import com.bookflow.authentication.domain.RegisteredUser;

import java.time.Instant;
import java.util.UUID;

public record RegisteredUserResponse(
        UUID id,
        String email,
        String status,
        Instant createdAt
) {
    static RegisteredUserResponse from(RegisteredUser user) {
        return new RegisteredUserResponse(
                user.id(),
                user.email(),
                user.status().name(),
                user.createdAt()
        );
    }
}
