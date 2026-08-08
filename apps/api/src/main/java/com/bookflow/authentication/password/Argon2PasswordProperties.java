package com.bookflow.authentication.password;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("bookflow.authentication.password.argon2")
public record Argon2PasswordProperties(
        @Min(16) int saltLength,
        @Min(16) int hashLength,
        @Min(1) int parallelism,
        @Min(19456) int memoryKib,
        @Min(2) int iterations
) {
}
