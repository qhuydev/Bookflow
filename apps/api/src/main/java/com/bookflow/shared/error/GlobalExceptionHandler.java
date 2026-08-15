package com.bookflow.shared.error;

import com.bookflow.authentication.application.EmailAlreadyRegisteredException;
import com.bookflow.authentication.application.InvalidCredentialsException;
import com.bookflow.authentication.application.InvalidPasswordResetTokenException;
import com.bookflow.authentication.application.RefreshTokenException;
import com.bookflow.businesses.application.BusinessSlugAlreadyExistsException;
import com.bookflow.businesses.application.CurrentBusinessUserUnavailableException;
import com.bookflow.businesses.authorization.TenantPermissionDeniedException;
import com.bookflow.branches.application.BranchCodeAlreadyExistsException;
import com.bookflow.employees.application.EmployeeCodeAlreadyExistsException;
import com.bookflow.schedules.application.ScheduleConflictException;
import com.bookflow.businesses.members.application.MemberConflictException;
import com.bookflow.authentication.ratelimit.RateLimitExceededException;
import com.bookflow.authentication.ratelimit.RateLimitUnavailableException;
import com.bookflow.bookings.application.IdempotencyKeyReusedException;
import com.bookflow.bookings.application.BookingConflictException;
import com.bookflow.bookings.application.BookingStateChangedException;
import com.bookflow.bookings.application.SlotUnavailableException;
import com.bookflow.bookings.domain.InvalidBookingTransitionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String OBJECT_LEVEL_FIELD = "$";
    private static final Comparator<ApiFieldViolation> VIOLATION_ORDER = Comparator
            .comparing(ApiFieldViolation::field)
            .thenComparing(ApiFieldViolation::code)
            .thenComparing(ApiFieldViolation::message);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException exception, WebRequest request) {
        return problem(ApiErrorCode.RESOURCE_NOT_FOUND, exception.getPublicDetail(), request, List.of());
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<Object> handleRequestValidation(RequestValidationException exception, WebRequest request) {
        return problem(ApiErrorCode.VALIDATION_ERROR, null, request, stable(exception.violations()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<Object> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.EMAIL_ALREADY_REGISTERED, null, request, List.of());
    }

    @ExceptionHandler(BusinessSlugAlreadyExistsException.class)
    ResponseEntity<Object> handleBusinessSlugAlreadyExists(
            BusinessSlugAlreadyExistsException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.BUSINESS_SLUG_ALREADY_EXISTS, null, request, List.of());
    }

    @ExceptionHandler(BranchCodeAlreadyExistsException.class)
    ResponseEntity<Object> handleBranchCodeAlreadyExists(BranchCodeAlreadyExistsException exception, WebRequest request) {
        return problem(ApiErrorCode.BRANCH_CODE_ALREADY_EXISTS, null, request, List.of());
    }

    @ExceptionHandler(EmployeeCodeAlreadyExistsException.class)
    ResponseEntity<Object> handleEmployeeCodeAlreadyExists(EmployeeCodeAlreadyExistsException exception, WebRequest request) {
        return problem(ApiErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS, null, request, List.of());
    }
    @ExceptionHandler(MemberConflictException.class)
    ResponseEntity<Object> handleMemberConflict(MemberConflictException exception, WebRequest request) { return problem(ApiErrorCode.MEMBER_CONFLICT, null, request, List.of()); }

    @ExceptionHandler(ScheduleConflictException.class)
    ResponseEntity<Object> handleScheduleConflict(ScheduleConflictException exception, WebRequest request) {
        return problem(ApiErrorCode.SCHEDULE_CONFLICT, null, request, List.of());
    }

    @ExceptionHandler(SlotUnavailableException.class)
    ResponseEntity<Object> handleSlotUnavailable(SlotUnavailableException exception, WebRequest request) {
        return problem(ApiErrorCode.SLOT_UNAVAILABLE, null, request, List.of());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<Object> handleIdempotencyKeyReused(
            IdempotencyKeyReusedException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, null, request, List.of());
    }

    @ExceptionHandler({
            BookingConflictException.class,
            BookingStateChangedException.class,
            InvalidBookingTransitionException.class
    })
    ResponseEntity<Object> handleBookingStateConflict(RuntimeException exception, WebRequest request) {
        return problem(ApiErrorCode.BOOKING_STATE_CONFLICT, null, request, List.of());
    }

    @ExceptionHandler(CurrentBusinessUserUnavailableException.class)
    ResponseEntity<Object> handleCurrentBusinessUserUnavailable(
            CurrentBusinessUserUnavailableException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.AUTH_CURRENT_USER_UNAVAILABLE, null, request, List.of());
    }

    @ExceptionHandler(TenantPermissionDeniedException.class)
    ResponseEntity<Object> handleTenantPermissionDenied(
            TenantPermissionDeniedException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.TENANT_PERMISSION_DENIED, null, request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException exception, WebRequest request) {
        return problem(ApiErrorCode.AUTH_INVALID_CREDENTIALS, null, request, List.of());
    }
    @ExceptionHandler(RefreshTokenException.class)
    ResponseEntity<Object> handleRefresh(RefreshTokenException exception, WebRequest request) {
        ApiErrorCode code = exception.kind == RefreshTokenException.Kind.MISSING ? ApiErrorCode.AUTH_REFRESH_MISSING : exception.kind == RefreshTokenException.Kind.REUSE ? ApiErrorCode.AUTH_REFRESH_REUSE : ApiErrorCode.AUTH_REFRESH_INVALID;
        return problem(code, null, request, List.of());
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    ResponseEntity<Object> handleInvalidPasswordResetToken(
            InvalidPasswordResetTokenException exception,
            WebRequest request
    ) {
        return problem(ApiErrorCode.AUTH_PASSWORD_RESET_INVALID, null, request, List.of());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Object> handleRateLimitExceeded(RateLimitExceededException exception, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        return problem(ApiErrorCode.AUTH_RATE_LIMITED, null, request, List.of(), headers);
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    ResponseEntity<Object> handleRateLimitUnavailable(RateLimitUnavailableException exception, WebRequest request) {
        return problem(ApiErrorCode.AUTH_RATE_LIMIT_UNAVAILABLE, null, request, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        List<ApiFieldViolation> violations = exception.getConstraintViolations().stream()
                .map(this::toFieldViolation)
                .sorted(VIOLATION_ORDER)
                .distinct()
                .toList();
        return problem(ApiErrorCode.VALIDATION_ERROR, null, request, violations);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        LOG.error("Unhandled API exception for request path {}", requestPath(request), exception);
        return problem(ApiErrorCode.INTERNAL_ERROR, null, request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .forEach(violations::add);
        exception.getBindingResult().getGlobalErrors().stream()
                .map(this::toFieldViolation)
                .forEach(violations::add);
        return problem(ApiErrorCode.VALIDATION_ERROR, null, request, stable(violations), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.add(new ApiFieldViolation(field, errorCode(error), publicMessage(error)));
            }
        }
        exception.getCrossParameterValidationResults().stream()
                .map(error -> new ApiFieldViolation(
                        OBJECT_LEVEL_FIELD,
                        errorCode(error),
                        publicMessage(error)
                ))
                .forEach(violations::add);
        return problem(ApiErrorCode.VALIDATION_ERROR, null, request, stable(violations), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.MALFORMED_REQUEST, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.INVALID_REQUEST, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.INVALID_REQUEST, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.ENDPOINT_NOT_FOUND, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.ENDPOINT_NOT_FOUND, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.METHOD_NOT_ALLOWED, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return problem(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, null, request, List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ApiErrorCode code = status.is5xxServerError()
                ? ApiErrorCode.INTERNAL_ERROR
                : ApiErrorCode.INVALID_REQUEST;
        if (status.is5xxServerError()) {
            LOG.error("Unhandled Spring MVC exception for request path {}", requestPath(request), exception);
        }
        return problem(code, null, request, List.of(), headers);
    }

    private ResponseEntity<Object> problem(
            ApiErrorCode code,
            String detail,
            WebRequest request,
            List<ApiFieldViolation> violations
    ) {
        return problem(code, detail, request, violations, HttpHeaders.EMPTY);
    }

    private ResponseEntity<Object> problem(
            ApiErrorCode code,
            String detail,
            WebRequest request,
            List<ApiFieldViolation> violations,
            HttpHeaders sourceHeaders
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                code.status(),
                detail == null ? code.detail() : detail
        );
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        if (!violations.isEmpty()) {
            problem.setProperty("violations", violations);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(sourceHeaders);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, code.status());
    }

    private ApiFieldViolation toFieldViolation(FieldError error) {
        return new ApiFieldViolation(error.getField(), errorCode(error), publicMessage(error));
    }

    private ApiFieldViolation toFieldViolation(ObjectError error) {
        return new ApiFieldViolation(OBJECT_LEVEL_FIELD, errorCode(error), publicMessage(error));
    }

    private ApiFieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        if (field.isBlank() || field.contains("(")) {
            field = "parameter";
        }
        String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return new ApiFieldViolation(field, code, safeText(violation.getMessage(), "Invalid value."));
    }

    private String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = firstNonBlank(requestParam.name(), requestParam.value());
            if (name != null) {
                return name;
            }
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = firstNonBlank(pathVariable.name(), pathVariable.value());
            if (name != null) {
                return name;
            }
        }
        return safeText(parameter.getParameterName(), "parameter");
    }

    private String errorCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0) {
            return "Invalid";
        }
        String code = codes[codes.length - 1];
        int separator = code.lastIndexOf('.');
        return separator >= 0 ? code.substring(separator + 1) : code;
    }

    private String publicMessage(MessageSourceResolvable error) {
        return safeText(error.getDefaultMessage(), "Invalid value.");
    }

    private List<ApiFieldViolation> stable(List<ApiFieldViolation> violations) {
        return violations.stream().sorted(VIOLATION_ORDER).distinct().toList();
    }

    private String requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            String requestUri = servletWebRequest.getRequest().getRequestURI();
            return requestUri.isBlank() ? "/" : requestUri;
        }
        return "/";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
