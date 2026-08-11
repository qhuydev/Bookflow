package com.bookflow.businesses.members.api;
import java.util.UUID;
public record CreateMemberRequest(String email, String role, UUID employeeId) {}
