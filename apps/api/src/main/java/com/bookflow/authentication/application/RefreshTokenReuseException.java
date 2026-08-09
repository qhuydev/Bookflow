package com.bookflow.authentication.application;

public final class RefreshTokenReuseException extends RefreshTokenException {
    public RefreshTokenReuseException() {
        super(Kind.REUSE);
    }
}
