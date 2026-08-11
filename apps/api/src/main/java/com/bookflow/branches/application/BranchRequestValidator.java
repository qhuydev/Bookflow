package com.bookflow.branches.application;

import com.bookflow.branches.api.CreateBranchRequest;
import com.bookflow.branches.api.UpdateBranchRequest;
import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class BranchRequestValidator {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]+(?:-[A-Z0-9]+)*$");
    private static final Pattern PHONE = Pattern.compile("^[+()0-9 .-]{7,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public ValidatedBranchCreate validateCreate(CreateBranchRequest request, String defaultTimeZone) {
        if (request == null) throw invalid("$", "NotNull", "Request body is required.");
        List<ApiFieldViolation> violations = unknown(request.unknownFields());
        String code = required(request.getCode(), "code", 50, violations, true);
        String name = required(request.getName(), "name", 200, violations, false);
        String addressLine1 = required(request.getAddressLine1(), "addressLine1", 200, violations, false);
        String city = required(request.getCity(), "city", 100, violations, false);
        String countryCode = country(request.getCountryCode(), violations);
        String timeZone = timezone(request.getTimeZone(), defaultTimeZone, violations);
        OptionalFields optional = optional(request.getAddressLine2(), request.getWard(), request.getDistrict(), request.getPostalCode(), request.getPhone(), request.getEmail(), violations);
        fail(violations);
        return new ValidatedBranchCreate(code, name, addressLine1, optional.addressLine2(), optional.ward(), optional.district(), city,
                optional.postalCode(), countryCode, optional.phone(), optional.email(), timeZone);
    }

    public ValidatedBranchUpdate validateUpdate(UpdateBranchRequest request) {
        if (request == null) throw invalid("$", "NotNull", "Request body is required.");
        List<ApiFieldViolation> violations = unknown(request.unknownFields());
        if (!request.hasKnownField()) violations.add(new ApiFieldViolation("$", "NotEmpty", "At least one branch setting is required."));
        String code = nullableRequired(request.getCode(), "code", 50, violations, true);
        String name = nullableRequired(request.getName(), "name", 200, violations, false);
        String addressLine1 = nullableRequired(request.getAddressLine1(), "addressLine1", 200, violations, false);
        String city = nullableRequired(request.getCity(), "city", 100, violations, false);
        String countryCode = request.getCountryCode() == null ? null : country(request.getCountryCode(), violations);
        String timeZone = request.getTimeZone() == null ? null : timezone(request.getTimeZone(), null, violations);
        OptionalFields optional = optional(request.getAddressLine2(), request.getWard(), request.getDistrict(), request.getPostalCode(), request.getPhone(), request.getEmail(), violations);
        fail(violations);
        return new ValidatedBranchUpdate(code, name, addressLine1, optional.addressLine2(), optional.ward(), optional.district(), city,
                optional.postalCode(), countryCode, optional.phone(), optional.email(), timeZone);
    }

    private List<ApiFieldViolation> unknown(Set<String> fields) {
        return fields.stream().sorted().map(field -> new ApiFieldViolation(field, "Forbidden", "This field is not allowed.")).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    private String required(String raw, String field, int max, List<ApiFieldViolation> violations, boolean code) {
        if (raw == null) { violations.add(new ApiFieldViolation(field, "NotBlank", "Value is required.")); return null; }
        return checked(raw, field, max, violations, code);
    }
    private String nullableRequired(String raw, String field, int max, List<ApiFieldViolation> violations, boolean code) {
        return raw == null ? null : checked(raw, field, max, violations, code);
    }
    private String checked(String raw, String field, int max, List<ApiFieldViolation> violations, boolean code) {
        String value = raw.strip();
        if (value.isEmpty() || value.length() > max) { violations.add(new ApiFieldViolation(field, value.isEmpty() ? "NotBlank" : "Size", "Value is invalid.")); return null; }
        if (code) {
            value = value.toUpperCase(Locale.ROOT);
            if (!CODE.matcher(value).matches()) { violations.add(new ApiFieldViolation(field, "Pattern", "Branch code format is invalid.")); return null; }
        }
        return value;
    }
    private String country(String raw, List<ApiFieldViolation> violations) {
        if (raw == null) { violations.add(new ApiFieldViolation("countryCode", "NotBlank", "Value is required.")); return null; }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        boolean known = value.length() == 2 && java.util.Arrays.asList(Locale.getISOCountries()).contains(value);
        if (!known) { violations.add(new ApiFieldViolation("countryCode", "Country", "Country code is invalid.")); return null; }
        return value;
    }
    private String timezone(String raw, String fallback, List<ApiFieldViolation> violations) {
        String value = raw == null ? fallback : raw.strip();
        if (value == null) return null;
        try {
            String canonical = ZoneId.of(value).getId();
            if (!"UTC".equals(canonical) && !ZoneId.getAvailableZoneIds().contains(canonical)) throw new DateTimeException("not IANA");
            return canonical;
        } catch (DateTimeException ex) { violations.add(new ApiFieldViolation("timeZone", "ZoneId", "Time zone is invalid.")); return null; }
    }
    private OptionalFields optional(String addressLine2, String ward, String district, String postalCode, String phone, String email, List<ApiFieldViolation> violations) {
        String a2 = optionalText(addressLine2, "addressLine2", 200, violations);
        String w = optionalText(ward, "ward", 100, violations);
        String d = optionalText(district, "district", 100, violations);
        String postal = optionalText(postalCode, "postalCode", 20, violations);
        String normalizedPhone = optionalText(phone, "phone", 30, violations);
        if (normalizedPhone != null && !PHONE.matcher(normalizedPhone).matches()) violations.add(new ApiFieldViolation("phone", "Pattern", "Phone is invalid."));
        String normalizedEmail = optionalText(email, "email", 254, violations);
        if (normalizedEmail != null && !EMAIL.matcher(normalizedEmail).matches()) violations.add(new ApiFieldViolation("email", "Email", "Email is invalid."));
        return new OptionalFields(a2, w, d, postal, normalizedPhone, normalizedEmail);
    }
    private String optionalText(String raw, String field, int max, List<ApiFieldViolation> violations) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.isEmpty() || value.length() > max) { violations.add(new ApiFieldViolation(field, value.isEmpty() ? "NotBlank" : "Size", "Value is invalid.")); return null; }
        return value;
    }
    private void fail(List<ApiFieldViolation> violations) { if (!violations.isEmpty()) throw new RequestValidationException(violations); }
    private RequestValidationException invalid(String field, String code, String message) { return new RequestValidationException(List.of(new ApiFieldViolation(field, code, message))); }
    private record OptionalFields(String addressLine2, String ward, String district, String postalCode, String phone, String email) { }
    public record ValidatedBranchCreate(String code, String name, String addressLine1, String addressLine2, String ward, String district, String city, String postalCode, String countryCode, String phone, String email, String timeZone) { }
    public record ValidatedBranchUpdate(String code, String name, String addressLine1, String addressLine2, String ward, String district, String city, String postalCode, String countryCode, String phone, String email, String timeZone) { }
}
