package com.bookflow.businesses.application;

import com.bookflow.businesses.api.UpdateBusinessRequest;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.CancellationPolicy;
import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class BusinessUpdateRequestValidator {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    public ValidatedBusinessUpdate validate(UpdateBusinessRequest request) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        if (request == null) {
            throw new RequestValidationException(List.of(new ApiFieldViolation("$", "NotNull", "Request body is required.")));
        }
        request.unknownFields().stream().sorted()
                .map(field -> new ApiFieldViolation(field, "Forbidden", "This field is not allowed."))
                .forEach(violations::add);
        if (!request.hasKnownField()) {
            violations.add(new ApiFieldViolation("$", "NotEmpty", "At least one business setting is required."));
        }
        String name = request.getName() == null ? null : optionalTrim(request.getName());
        if (request.getName() != null && (name.isEmpty() || name.length() > 200)) {
            violations.add(new ApiFieldViolation("name", name.isEmpty() ? "NotBlank" : "Size", "Business name is invalid."));
        }
        String slug = request.getSlug() == null ? null : optionalTrim(request.getSlug()).toLowerCase(Locale.ROOT);
        if (slug != null && (slug.isEmpty() || slug.length() > 100 || !SLUG.matcher(slug).matches())) {
            violations.add(new ApiFieldViolation("slug", "Pattern", "Business slug format is invalid."));
        }
        BusinessType type = parseEnum(request.getType(), BusinessType.class, "type", violations);
        String timeZone = parseTimeZone(request.getTimeZone(), violations);
        String currency = parseCurrency(request.getCurrencyCode(), violations);
        CancellationPolicy policy = parseEnum(request.getCancellationPolicy(), CancellationPolicy.class, "cancellationPolicy", violations);
        Integer days = request.getMaxBookingAdvanceDays();
        if (days != null && (days < 0 || days > 365)) {
            violations.add(new ApiFieldViolation("maxBookingAdvanceDays", "Range", "Maximum booking advance must be between 0 and 365."));
        }
        if (!violations.isEmpty()) { throw new RequestValidationException(violations); }
        return new ValidatedBusinessUpdate(name, slug, type, timeZone, currency, policy, days);
    }

    private String optionalTrim(String value) { return value.strip(); }
    private <T extends Enum<T>> T parseEnum(String raw, Class<T> type, String field, List<ApiFieldViolation> violations) {
        if (raw == null) return null;
        try { return Enum.valueOf(type, optionalTrim(raw).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { violations.add(new ApiFieldViolation(field, "Enum", "Value is invalid.")); return null; }
    }
    private String parseTimeZone(String raw, List<ApiFieldViolation> violations) {
        if (raw == null) return null;
        try {
            String canonical = ZoneId.of(optionalTrim(raw)).getId();
            if (!"UTC".equals(canonical) && !ZoneId.getAvailableZoneIds().contains(canonical)) throw new DateTimeException("not IANA");
            return canonical;
        } catch (DateTimeException ex) { violations.add(new ApiFieldViolation("timeZone", "ZoneId", "Time zone is invalid.")); return null; }
    }
    private String parseCurrency(String raw, List<ApiFieldViolation> violations) {
        if (raw == null) return null;
        try { return Currency.getInstance(optionalTrim(raw).toUpperCase(Locale.ROOT)).getCurrencyCode(); }
        catch (IllegalArgumentException ex) { violations.add(new ApiFieldViolation("currencyCode", "Currency", "Currency is invalid.")); return null; }
    }

    public record ValidatedBusinessUpdate(String name, String slug, BusinessType type, String timeZone,
                                         String currencyCode, CancellationPolicy cancellationPolicy,
                                         Integer maxBookingAdvanceDays) { }
}
