package com.bookflow.authentication.api;

public record ResetPasswordRequest(String token, String newPassword) { }
