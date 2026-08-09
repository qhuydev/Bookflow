package com.bookflow.authentication.ratelimit;

public class RateLimitUnavailableException extends RuntimeException {
    public RateLimitUnavailableException(Throwable cause) {
        super("Authentication rate limiter is unavailable.", cause);
    }
}
