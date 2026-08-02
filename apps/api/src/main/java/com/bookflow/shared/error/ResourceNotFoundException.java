package com.bookflow.shared.error;

import java.util.Objects;

public final class ResourceNotFoundException extends RuntimeException {

    private static final String DEFAULT_DETAIL = "The requested resource was not found.";

    private final String publicDetail;

    public ResourceNotFoundException() {
        this(DEFAULT_DETAIL);
    }

    public ResourceNotFoundException(String publicDetail) {
        super(DEFAULT_DETAIL);
        this.publicDetail = requirePublicDetail(publicDetail);
    }

    public String getPublicDetail() {
        return publicDetail;
    }

    private static String requirePublicDetail(String publicDetail) {
        String detail = Objects.requireNonNull(publicDetail, "publicDetail must not be null").trim();
        if (detail.isEmpty()) {
            throw new IllegalArgumentException("publicDetail must not be blank");
        }
        return detail;
    }
}
