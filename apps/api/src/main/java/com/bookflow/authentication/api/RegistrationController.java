package com.bookflow.authentication.api;

import com.bookflow.authentication.application.UserRegistrationService;
import com.bookflow.authentication.domain.RegisteredUser;
import com.bookflow.authentication.application.EmailNormalizer;
import com.bookflow.authentication.ratelimit.AuthenticationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@Validated
@Profile("!test")
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;
    private final AuthenticationRateLimiter rateLimiter;

    public RegistrationController(
            UserRegistrationService userRegistrationService,
            AuthenticationRateLimiter rateLimiter
    ) {
        this.userRegistrationService = userRegistrationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a global BookFlow user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered"),
            @ApiResponse(responseCode = "400", description = "Invalid registration request"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<RegisteredUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.check("register", EmailNormalizer.normalize(request.email()), servletRequest);
        RegisteredUser user = userRegistrationService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisteredUserResponse.from(user));
    }
}
