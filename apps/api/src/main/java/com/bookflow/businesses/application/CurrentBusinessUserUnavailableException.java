package com.bookflow.businesses.application;

public class CurrentBusinessUserUnavailableException extends RuntimeException {
    public CurrentBusinessUserUnavailableException() {
        super("The authenticated user is unavailable.");
    }
}
