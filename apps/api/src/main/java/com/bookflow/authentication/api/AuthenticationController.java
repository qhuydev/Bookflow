package com.bookflow.authentication.api;

import com.bookflow.authentication.application.UserLoginService;
import com.bookflow.authentication.application.RefreshTokenException;
import com.bookflow.authentication.application.EmailNormalizer;
import com.bookflow.authentication.config.AuthenticationProperties;
import com.bookflow.authentication.ratelimit.AuthenticationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/auth") @Profile("!test") @Tag(name = "Authentication")
public class AuthenticationController {
 private final UserLoginService login; private final AuthenticationProperties props; private final AuthenticationRateLimiter rateLimiter;
 public AuthenticationController(UserLoginService login,AuthenticationProperties props,AuthenticationRateLimiter rateLimiter){this.login=login;this.props=props;this.rateLimiter=rateLimiter;}
 @GetMapping("/csrf") @Operation(summary = "Cấp CSRF token cho authentication request") @ApiResponse(responseCode = "200", description = "CSRF token issued") public CsrfTokenResponse csrf(CsrfToken token){return new CsrfTokenResponse(token.getToken(),token.getHeaderName());}
 @PostMapping("/login")
 @Operation(summary = "Đăng nhập và cấp access token", description = "Gọi GET /api/v1/auth/csrf trước, rồi gửi giá trị token qua header X-XSRF-TOKEN. Cookie XSRF-TOKEN được trình duyệt Swagger giữ trong cùng phiên.")
 @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Giá trị token từ GET /api/v1/auth/csrf", example = "<copy-token-from-csrf-endpoint>")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = LoginRequest.class), examples = @ExampleObject(value = "{\"email\":\"demo.user@example.test\",\"password\":\"BookFlow demo password 2026!\"}")))
 @ApiResponses({
         @ApiResponse(responseCode = "200", description = "Authenticated", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
         @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
         @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
         @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
 })
 public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,HttpServletRequest servletRequest){rateLimiter.check("login",EmailNormalizer.normalize(request.email()),servletRequest);var result=login.login(request.email(),request.password()); ResponseCookie cookie=ResponseCookie.from(props.refreshCookie().name(),result.refreshToken()).httpOnly(true).secure(props.refreshCookie().secure()).sameSite(props.refreshCookie().sameSite()).path(props.refreshCookie().path()).maxAge(Duration.ofDays(props.session().absoluteDays())).build(); return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie.toString()).body(new LoginResponse(result.accessToken(),"Bearer",result.expiresIn()));}
 @PostMapping("/refresh") @Operation(summary="Rotate refresh token") @ApiResponses({@ApiResponse(responseCode="200",description="Access token renewed",content=@Content(schema=@Schema(implementation=LoginResponse.class))),@ApiResponse(responseCode="401",description="Refresh token invalid or reused",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="403",description="CSRF token missing or invalid",content=@Content(schema=@Schema(implementation=ProblemDetail.class)))}) public ResponseEntity<LoginResponse> refresh(@CookieValue(name="bookflow_refresh",required=false) String raw,HttpServletRequest servletRequest){rateLimiter.check("refresh",raw,servletRequest);var result=login.refresh(raw);return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,refreshCookie(result.refreshToken()).toString()).body(new LoginResponse(result.accessToken(),"Bearer",result.expiresIn()));}
 @PostMapping("/logout") @Operation(summary="Logout current refresh session") @ApiResponses({@ApiResponse(responseCode="204",description="Session revoked"),@ApiResponse(responseCode="403",description="CSRF token missing or invalid",content=@Content(schema=@Schema(implementation=ProblemDetail.class)))}) public ResponseEntity<Void> logout(@CookieValue(name="bookflow_refresh",required=false) String raw,HttpServletRequest servletRequest){rateLimiter.check("logout",raw,servletRequest);login.logout(raw);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,expiredCookie().toString()).build();}
 @PostMapping("/logout-all") @Operation(summary="Logout all refresh sessions for current user") @SecurityRequirement(name="bearerAuth") @ApiResponses({@ApiResponse(responseCode="204",description="Sessions revoked"),@ApiResponse(responseCode="401",description="Bearer token required",content=@Content(schema=@Schema(implementation=ProblemDetail.class))),@ApiResponse(responseCode="403",description="CSRF token missing or invalid",content=@Content(schema=@Schema(implementation=ProblemDetail.class)))}) public ResponseEntity<Void> logoutAll(Authentication authentication,HttpServletRequest servletRequest){if(authentication==null||!authentication.isAuthenticated()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();rateLimiter.check("logout-all",authentication.getName(),servletRequest); login.logoutAll(UUID.fromString(authentication.getName()));return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,expiredCookie().toString()).build();}
 private ResponseCookie refreshCookie(String raw){return ResponseCookie.from(props.refreshCookie().name(),raw).httpOnly(true).secure(props.refreshCookie().secure()).sameSite(props.refreshCookie().sameSite()).path(props.refreshCookie().path()).maxAge(Duration.ofDays(props.session().absoluteDays())).build();}
 private ResponseCookie expiredCookie(){return ResponseCookie.from(props.refreshCookie().name(),"").httpOnly(true).secure(props.refreshCookie().secure()).sameSite(props.refreshCookie().sameSite()).path(props.refreshCookie().path()).maxAge(Duration.ZERO).build();}
}
