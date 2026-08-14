package com.bookflow.bookings.application;

import com.bookflow.bookings.api.CreateBookingCustomerRequest;
import com.bookflow.bookings.api.CreateBookingRequest;
import com.bookflow.bookings.domain.BookingCustomer;
import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class BookingRequestValidator {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,200}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+()0-9 .-]{7,30}$");

    public ValidatedBookingRequest validate(
            String rawSlug,
            String rawIdempotencyKey,
            CreateBookingRequest request
    ) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        String slug = normalizeSlug(rawSlug, violations);
        String idempotencyKey = validateIdempotencyKey(rawIdempotencyKey, violations);
        if (request == null) {
            violations.add(new ApiFieldViolation("$", "NotNull", "Request body is required."));
            fail(violations);
        }
        request.unknownFields().stream().sorted().forEach(field -> violations.add(
                new ApiFieldViolation(field, "Forbidden", "This field is not allowed.")));
        UUID branchId = required(request.getBranchId(), "branchId", violations);
        UUID serviceId = required(request.getServiceId(), "serviceId", violations);
        UUID employeeId = required(request.getEmployeeId(), "employeeId", violations);
        OffsetDateTime start = required(request.getStart(), "start", violations);
        BookingCustomer customer = validateCustomer(request.getCustomer(), violations);
        fail(violations);
        return new ValidatedBookingRequest(
                slug, idempotencyKey, branchId, serviceId, employeeId, start, customer
        );
    }

    private BookingCustomer validateCustomer(
            CreateBookingCustomerRequest request,
            List<ApiFieldViolation> violations
    ) {
        if (request == null) {
            violations.add(new ApiFieldViolation("customer", "NotNull", "Customer is required."));
            return null;
        }
        request.unknownFields().stream().sorted().forEach(field -> violations.add(
                new ApiFieldViolation("customer." + field, "Forbidden", "This field is not allowed.")));
        String name = normalizeText(request.getName());
        if (name == null || name.length() > 200) {
            violations.add(new ApiFieldViolation("customer.name", name == null ? "NotBlank" : "Size",
                    "Customer name is invalid."));
        }
        String email = normalizeEmail(request.getEmail());
        if (email != null && (email.length() > 320 || !EMAIL.matcher(email).matches())) {
            violations.add(new ApiFieldViolation("customer.email", "Email", "Customer email is invalid."));
        }
        String phone = normalizeText(request.getPhone());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            violations.add(new ApiFieldViolation("customer.phone", "Pattern", "Customer phone is invalid."));
        }
        if (email == null && phone == null) {
            violations.add(new ApiFieldViolation("customer", "Contact", "Customer email or phone is required."));
        }
        if (name == null || name.length() > 200 || (email == null && phone == null)
                || (email != null && (email.length() > 320 || !EMAIL.matcher(email).matches()))
                || (phone != null && !PHONE.matcher(phone).matches())) {
            return null;
        }
        return new BookingCustomer(null, name, email, phone);
    }

    private String normalizeSlug(String raw, List<ApiFieldViolation> violations) {
        String slug = normalizeText(raw);
        if (slug == null) {
            violations.add(new ApiFieldViolation("slug", "NotBlank", "Business slug is required."));
            return null;
        }
        return slug.toLowerCase(Locale.ROOT);
    }

    private String validateIdempotencyKey(String raw, List<ApiFieldViolation> violations) {
        if (raw == null || !IDEMPOTENCY_KEY.matcher(raw).matches()) {
            violations.add(new ApiFieldViolation("Idempotency-Key", "Pattern",
                    "Idempotency-Key must contain 8 to 200 safe characters."));
            return null;
        }
        return raw;
    }

    private <T> T required(T value, String field, List<ApiFieldViolation> violations) {
        if (value == null) {
            violations.add(new ApiFieldViolation(field, "NotNull", "Value is required."));
        }
        return value;
    }

    private String normalizeEmail(String raw) {
        String value = normalizeText(raw);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    }

    private void fail(List<ApiFieldViolation> violations) {
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
    }

    public record ValidatedBookingRequest(
            String slug,
            String idempotencyKey,
            UUID branchId,
            UUID serviceId,
            UUID employeeId,
            OffsetDateTime start,
            BookingCustomer customer
    ) {
    }
}
