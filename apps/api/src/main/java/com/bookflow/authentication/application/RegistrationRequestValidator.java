package com.bookflow.authentication.application;

import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RegistrationRequestValidator {

    private static final int MAXIMUM_EMAIL_LENGTH = 320;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+$");

    private final PasswordPolicy passwordPolicy;

    public RegistrationRequestValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public void validate(String email, String password) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        String normalizedEmail = EmailNormalizer.normalize(email);

        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            violations.add(new ApiFieldViolation("email", "NotBlank", "Email is required."));
        } else if (normalizedEmail.length() > MAXIMUM_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            violations.add(new ApiFieldViolation("email", "Email", "Email must be valid."));
        }

        if (!passwordPolicy.isValid(password)) {
            violations.add(new ApiFieldViolation(
                    "password",
                    "PasswordPolicy",
                    "Password does not meet the password policy."
            ));
        }

        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
    }
}
