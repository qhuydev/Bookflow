package com.bookflow.authentication.api;
public record LoginResponse(String accessToken, String tokenType, long expiresIn) { }
