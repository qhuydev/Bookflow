package com.bookflow.authentication.application;

import com.bookflow.shared.error.RequestValidationException;
import com.bookflow.shared.error.ApiFieldViolation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginRequestValidator {
    public void validate(String email, String password) {
        if (email == null || email.isBlank()) throw new RequestValidationException(List.of(new ApiFieldViolation("email", "NotBlank", "Email is required.")));
        if (password == null || password.isEmpty()) throw new RequestValidationException(List.of(new ApiFieldViolation("password", "NotBlank", "Password is required.")));
    }
}
