package com.bookflow.authentication.application;

import java.text.Normalizer;
import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return Normalizer.normalize(email, Normalizer.Form.NFC)
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
