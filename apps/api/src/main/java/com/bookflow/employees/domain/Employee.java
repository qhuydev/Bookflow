package com.bookflow.employees.domain;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record Employee(UUID id, UUID tenantId, String code, String fullName, String phone, String email, String bio, EmployeeStatus status, List<UUID> branchIds, Instant createdAt, Instant updatedAt) { }
