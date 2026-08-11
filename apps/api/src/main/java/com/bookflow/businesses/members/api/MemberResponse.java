package com.bookflow.businesses.members.api;
import java.time.Instant; import java.util.UUID;
public record MemberResponse(UUID id, UUID userId, String email, String role, String status, UUID employeeId, Instant createdAt, Instant updatedAt) {}
