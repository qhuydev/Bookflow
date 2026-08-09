package com.bookflow.authentication.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationRateLimiter {
    void check(String action, String accountIdentifier, HttpServletRequest request);
}
