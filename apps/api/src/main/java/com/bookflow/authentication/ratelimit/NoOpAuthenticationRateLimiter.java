package com.bookflow.authentication.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "bookflow.authentication.rate-limit", name = "enabled", havingValue = "false")
public class NoOpAuthenticationRateLimiter implements AuthenticationRateLimiter {
    @Override
    public void check(String action, String accountIdentifier, HttpServletRequest request) { }
}
