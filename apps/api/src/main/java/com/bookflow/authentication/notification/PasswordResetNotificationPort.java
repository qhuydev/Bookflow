package com.bookflow.authentication.notification;

import java.time.Instant;

public interface PasswordResetNotificationPort {
    void sendPasswordReset(String normalizedEmail, String rawToken, Instant expiresAt);
}
