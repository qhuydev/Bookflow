package com.bookflow.authentication.domain;
public record RotatedRefreshToken(String rawToken, String hash) { }
