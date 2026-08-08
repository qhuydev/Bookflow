package com.bookflow.shared.error;

import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ApiErrorCode {
    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation failed",
            "Request validation failed."
    ),
    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            "malformed-request",
            "Malformed request",
            "The request body could not be read."
    ),
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "invalid-request",
            "Invalid request",
            "The request is invalid."
    ),
    EMAIL_ALREADY_REGISTERED(
            HttpStatus.CONFLICT,
            "email-already-registered",
            "Email already registered",
            "An account with this email already exists."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "resource-not-found",
            "Resource not found",
            "The requested resource was not found."
    ),
    ENDPOINT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "endpoint-not-found",
            "Endpoint not found",
            "The requested endpoint was not found."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "method-not-allowed",
            "Method not allowed",
            "The HTTP method is not supported for this endpoint."
    ),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "unsupported-media-type",
            "Unsupported media type",
            "The request media type is not supported."
    ),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal server error",
            "An unexpected error occurred."
    );

    private static final String TYPE_PREFIX = "urn:bookflow:problem:";

    private final HttpStatus status;
    private final URI type;
    private final String title;
    private final String detail;

    ApiErrorCode(HttpStatus status, String type, String title, String detail) {
        this.status = status;
        this.type = URI.create(TYPE_PREFIX + type);
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
