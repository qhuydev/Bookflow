package com.bookflow.authentication.api;

import com.bookflow.authentication.application.PasswordRecoveryService;
import com.bookflow.authentication.application.EmailNormalizer;
import com.bookflow.authentication.ratelimit.AuthenticationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("!test")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final AuthenticationRateLimiter rateLimiter;

    public PasswordRecoveryController(
            PasswordRecoveryService recovery,
            AuthenticationRateLimiter rateLimiter
    ) {
        this.recovery = recovery;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Yêu cầu đặt lại mật khẩu")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted regardless of account existence"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid")
    })
    public ResponseEntity<Void> forgotPassword(
            @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.check("forgot-password", EmailNormalizer.normalize(request.email()), servletRequest);
        recovery.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt mật khẩu mới bằng token dùng một lần")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password reset completed"),
            @ApiResponse(responseCode = "400", description = "Invalid request or reset token"),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid")
    })
    public ResponseEntity<Void> resetPassword(
            @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.check("reset-password", request.token(), servletRequest);
        recovery.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
