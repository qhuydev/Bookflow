package com.bookflow.authentication.password;

import com.bookflow.authentication.application.PasswordPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Argon2idPasswordHasher {

    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public Argon2idPasswordHasher(PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    public String hash(String password) {
        return passwordEncoder.encode(passwordPolicy.normalizeForHash(password));
    }

    public boolean matches(String password, String encodedPassword) {
        return passwordEncoder.matches(passwordPolicy.normalizeForHash(password), encodedPassword);
    }

    public boolean upgradeEncoding(String encodedPassword) {
        return passwordEncoder.upgradeEncoding(encodedPassword);
    }

    public boolean isArgon2id(String encodedPassword) {
        return passwordEncoder instanceof Argon2PasswordEncoder && encodedPassword.startsWith("$argon2id$");
    }
}
