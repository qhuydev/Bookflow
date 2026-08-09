package com.bookflow.authentication.notification;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NoOpPasswordResetNotificationAdapter implements PasswordResetNotificationPort {
    @Override
    public void sendPasswordReset(String normalizedEmail, String rawToken, Instant expiresAt) {
        // Email/SMS delivery is intentionally deferred. Never log the raw token here.
    }
}
