package com.bookflow.employees.api;
import com.bookflow.employees.domain.Employee; import java.time.Instant; import java.util.*;
public record EmployeeResponse(UUID id, UUID businessId, String code, String fullName, String phone, String email, String bio, String status, List<UUID> branchIds, Instant createdAt, Instant updatedAt) { public static EmployeeResponse from(Employee e){return new EmployeeResponse(e.id(),e.tenantId(),e.code(),e.fullName(),e.phone(),e.email(),e.bio(),e.status().name(),e.branchIds(),e.createdAt(),e.updatedAt());} }
