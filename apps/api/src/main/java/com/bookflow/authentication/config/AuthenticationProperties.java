package com.bookflow.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bookflow.authentication")
public record AuthenticationProperties(Session session, AccessToken accessToken, RefreshCookie refreshCookie) {
    public record Session(int inactivityDays, int absoluteDays) { }
    public record AccessToken(String issuer, String audience, String clientId, long expiresInSeconds) { }
    public record RefreshCookie(String name, boolean secure, String sameSite, String path) { }
}
