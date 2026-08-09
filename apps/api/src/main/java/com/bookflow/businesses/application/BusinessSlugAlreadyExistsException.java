package com.bookflow.businesses.application;

public class BusinessSlugAlreadyExistsException extends RuntimeException {
    public BusinessSlugAlreadyExistsException() {
        super("A business with this slug already exists.");
    }
}
