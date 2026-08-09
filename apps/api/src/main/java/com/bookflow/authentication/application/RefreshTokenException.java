package com.bookflow.authentication.application;
public class RefreshTokenException extends RuntimeException {
    public final Kind kind;
    public RefreshTokenException(Kind kind) { this.kind = kind; }
    public enum Kind { MISSING, INVALID, REUSE }
}
