package com.bookflow.authentication.api;

import jakarta.validation.constraints.NotNull;

public record RegisterUserRequest(
        @NotNull(message = "Email is required.") String email,
        @NotNull(message = "Password is required.") String password
) {
}
