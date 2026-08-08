package com.bookflow.authentication.application;

import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationRequestValidatorTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final RegistrationRequestValidator validator = new RegistrationRequestValidator(passwordPolicy);

    @Test
    void normalizesEmailWithNfcTrimAndLocaleRootLowercase() {
        String email = "  Huy@Example.COM  ";

        validator.validate(email, "lowercase password 2026!");

        assertThat(EmailNormalizer.normalize(email)).isEqualTo("huy@example.com");
    }

    @Test
    void appliesTheAcceptedPasswordPolicyWithoutMutatingWhitespace() {
        assertThat(passwordPolicy.isValid("lowercase password 2026!")).isTrue();
        assertThat(passwordPolicy.isValid("short password")).isFalse();
        assertThat(passwordPolicy.isValid(" ".repeat(15))).isFalse();
        assertThat(passwordPolicy.isValid("a".repeat(129))).isFalse();
    }

    @Test
    void reportsSafeViolationsForInvalidRegistrationInput() {
        assertThatThrownBy(() -> validator.validate(" not-an-email ", "short password"))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(exception -> {
                    RequestValidationException validationException = (RequestValidationException) exception;
                    assertThat(validationException.violations()).extracting(violation -> violation.field())
                            .containsExactly("email", "password");
                });
    }
}
