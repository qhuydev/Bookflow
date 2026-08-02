package com.bookflow.shared.error;

public record ApiFieldViolation(String field, String code, String message) {
}
