package com.bookflow.authentication.application;

import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PasswordRecoveryRequestValidator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private final PasswordPolicy passwordPolicy;

    public PasswordRecoveryRequestValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public void validateForgot(String email) {
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null || normalized.isBlank() || normalized.length() > 320
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RequestValidationException(List.of(
                    new ApiFieldViolation("email", "Email", "Email must be valid.")
            ));
        }
    }

    public void validateReset(String token, String password) {
        if (token == null || token.isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }
        if (!passwordPolicy.isValid(password)) {
            throw new RequestValidationException(List.of(
                    new ApiFieldViolation("password", "PasswordPolicy", "Password does not meet the password policy.")
            ));
        }
    }
}
