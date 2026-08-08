package com.bookflow.shared.error;

import java.util.List;

public final class RequestValidationException extends RuntimeException {

    private final List<ApiFieldViolation> violations;

    public RequestValidationException(List<ApiFieldViolation> violations) {
        this.violations = List.copyOf(violations);
    }

    public List<ApiFieldViolation> violations() {
        return violations;
    }
}
