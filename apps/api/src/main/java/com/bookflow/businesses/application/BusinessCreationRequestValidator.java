package com.bookflow.businesses.application;

import com.bookflow.businesses.api.CreateBusinessRequest;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class BusinessCreationRequestValidator {

    private static final int MAXIMUM_NAME_LENGTH = 200;
    private static final int MAXIMUM_SLUG_LENGTH = 100;
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public ValidatedBusinessCreation validate(CreateBusinessRequest request) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        if (request == null) {
            throw new RequestValidationException(List.of(
                    new ApiFieldViolation("$", "NotNull", "Request body is required.")
            ));
        }

        if (!request.unknownFields().isEmpty()) {
            request.unknownFields().stream()
                    .sorted()
                    .map(field -> new ApiFieldViolation(field, "Forbidden", "This field is not allowed."))
                    .forEach(violations::add);
        }

        String name = trim(request.name());
        if (name == null || name.isEmpty()) {
            violations.add(new ApiFieldViolation("name", "NotBlank", "Business name is required."));
        } else if (name.length() > MAXIMUM_NAME_LENGTH) {
            violations.add(new ApiFieldViolation("name", "Size", "Business name is too long."));
        }

        String slug = normalizeSlug(request.slug());
        if (slug == null || slug.isEmpty()) {
            violations.add(new ApiFieldViolation("slug", "NotBlank", "Business slug is required."));
        } else if (slug.length() > MAXIMUM_SLUG_LENGTH || !SLUG_PATTERN.matcher(slug).matches()) {
            violations.add(new ApiFieldViolation("slug", "Pattern", "Business slug format is invalid."));
        }

        BusinessType businessType = parseBusinessType(request.type(), violations);
        String timeZone = parseTimeZone(request.timeZone(), violations);

        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return new ValidatedBusinessCreation(name, slug, businessType, timeZone);
    }

    private BusinessType parseBusinessType(String rawType, List<ApiFieldViolation> violations) {
        String type = trim(rawType);
        if (type == null || type.isEmpty()) {
            violations.add(new ApiFieldViolation("type", "NotBlank", "Business type is required."));
            return null;
        }
        try {
            return BusinessType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            violations.add(new ApiFieldViolation("type", "Enum", "Business type is invalid."));
            return null;
        }
    }

    private String parseTimeZone(String rawTimeZone, List<ApiFieldViolation> violations) {
        String timeZone = trim(rawTimeZone);
        if (timeZone == null || timeZone.isEmpty()) {
            violations.add(new ApiFieldViolation("timeZone", "NotBlank", "Time zone is required."));
            return null;
        }
        try {
            String canonical = ZoneId.of(timeZone).getId();
            if (!"UTC".equals(canonical) && !ZoneId.getAvailableZoneIds().contains(canonical)) {
                throw new DateTimeException("Time zone must use an IANA ID.");
            }
            return canonical;
        } catch (DateTimeException exception) {
            violations.add(new ApiFieldViolation("timeZone", "ZoneId", "Time zone is invalid."));
            return null;
        }
    }

    private String normalizeSlug(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.strip();
    }

    public record ValidatedBusinessCreation(String name, String slug, BusinessType businessType, String timeZone) {
    }
}
