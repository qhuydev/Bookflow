package com.bookflow.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bookflow.authentication")
public record AuthenticationProperties(
        Session session,
        AccessToken accessToken,
        RefreshCookie refreshCookie,
        Cors cors,
        PasswordReset passwordReset,
        RateLimit rateLimit
) {
    public record Session(int inactivityDays, int absoluteDays) { }
    public record AccessToken(String issuer, String audience, String clientId, long expiresInSeconds) { }
    public record RefreshCookie(String name, boolean secure, String sameSite, String path) { }
    public record Cors(java.util.List<String> allowedOrigins) { }
    public record PasswordReset(long ttlMinutes) { }
    public record RateLimit(
            boolean enabled,
            boolean failOpen,
            long windowSeconds,
            long accountLimit,
            long ipLimit,
            java.util.List<String> trustedProxies
    ) { }
}
