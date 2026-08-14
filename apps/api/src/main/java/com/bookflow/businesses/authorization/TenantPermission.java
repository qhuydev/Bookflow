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
    EMPLOYEE_MANAGE,
    MEMBER_VIEW,
    MEMBER_MANAGE,
    SERVICE_VIEW,
    SERVICE_MANAGE,
    SCHEDULE_VIEW,
    SCHEDULE_MANAGE
}
