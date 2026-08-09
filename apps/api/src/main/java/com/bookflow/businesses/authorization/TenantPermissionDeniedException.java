package com.bookflow.businesses.authorization;

public final class TenantPermissionDeniedException extends RuntimeException {
    public TenantPermissionDeniedException() {
        super("Tenant permission is denied.");
    }
}
