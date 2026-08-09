package com.bookflow.authentication.application;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("Password reset token is invalid.");
    }
}
