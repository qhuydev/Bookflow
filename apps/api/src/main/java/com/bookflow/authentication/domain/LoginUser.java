package com.bookflow.authentication.domain;

import java.util.UUID;

public record LoginUser(UUID id, String normalizedEmail, String passwordHash, UserStatus status) { }
