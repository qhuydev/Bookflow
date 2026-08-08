package com.bookflow.authentication.password;

import com.bookflow.authentication.application.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2idPasswordHasherTest {

    private final Argon2idPasswordHasher passwordHasher = new Argon2idPasswordHasher(
            new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
            new PasswordPolicy()
    );

    @Test
    void hashesPasswordsWithArgon2idAndRandomSalt() {
        String password = "BookFlow registration 2026!";

        String firstHash = passwordHasher.hash(password);
        String secondHash = passwordHasher.hash(password);

        assertThat(firstHash).startsWith("$argon2id$");
        assertThat(firstHash).isNotEqualTo(password);
        assertThat(secondHash).isNotEqualTo(firstHash);
        assertThat(passwordHasher.matches(password, firstHash)).isTrue();
        assertThat(passwordHasher.matches(password, secondHash)).isTrue();
        assertThat(passwordHasher.matches("wrong password", firstHash)).isFalse();
        assertThat(passwordHasher.upgradeEncoding(firstHash)).isFalse();
    }
}
