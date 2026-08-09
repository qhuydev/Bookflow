package com.bookflow.authentication.token;

import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public record JwtSigningMaterial(JwtEncoder encoder, JwtDecoder decoder, String keyId) { }
