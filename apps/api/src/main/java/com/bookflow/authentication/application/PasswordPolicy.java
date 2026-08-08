package com.bookflow.authentication.application;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    static final int MINIMUM_LENGTH = 15;
    static final int MAXIMUM_LENGTH = 128;

    public boolean isValid(String password) {
        if (password == null) {
            return false;
        }

        String normalized = normalizeForHash(password);
        int length = normalized.codePointCount(0, normalized.length());
        return length >= MINIMUM_LENGTH
                && length <= MAXIMUM_LENGTH
                && !normalized.strip().isEmpty();
    }

    public String normalizeForHash(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFC);
    }
}
