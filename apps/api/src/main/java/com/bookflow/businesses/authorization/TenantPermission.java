package com.bookflow.businesses.authorization;

/** Permissions follow ADR 0002; future endpoints must request a permission, never compare role ordinals. */
public enum TenantPermission {
    BUSINESS_VIEW,
    BUSINESS_CONFIGURATION_MANAGE,
    MEMBERSHIP_STAFF_MANAGE,
    MEMBERSHIP_PRIVILEGED_MANAGE,
    BUSINESS_CLOSE,
    BRANCH_VIEW,
    BRANCH_MANAGE,
    EMPLOYEE_VIEW,
    EMPLOYEE_MANAGE
}
