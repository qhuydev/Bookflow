package com.bookflow.bookings.application;

import com.bookflow.bookings.api.CancelBookingRequest;
import com.bookflow.bookings.api.RescheduleBookingRequest;
import com.bookflow.shared.error.ApiFieldViolation;
import com.bookflow.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BookingLifecycleValidator {
    public String cancelReason(CancelBookingRequest request) {
        if (request == null) {
            return null;
        }
        List<ApiFieldViolation> violations = new ArrayList<>();
        request.unknownFields().stream().sorted().forEach(field -> violations.add(
                new ApiFieldViolation(field, "Forbidden", "This field is not allowed.")));
        String reason = reason(request.getReason(), violations);
        fail(violations);
        return reason;
    }

    public ValidatedReschedule validate(RescheduleBookingRequest request) {
        List<ApiFieldViolation> violations = new ArrayList<>();
        if (request == null) {
            violations.add(new ApiFieldViolation("$", "NotNull", "Request body is required."));
            fail(violations);
        }
        request.unknownFields().stream().sorted().forEach(field -> violations.add(
                new ApiFieldViolation(field, "Forbidden", "This field is not allowed.")));
        OffsetDateTime start = request.getStart();
        if (start == null) {
            violations.add(new ApiFieldViolation("start", "NotNull", "Start is required."));
        }
        String reason = reason(request.getReason(), violations);
        fail(violations);
        return new ValidatedReschedule(start, request.getEmployeeId(), reason);
    }

    private String reason(String raw, List<ApiFieldViolation> violations) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        if (value.length() > 500) {
            violations.add(new ApiFieldViolation("reason", "Size", "Reason must not exceed 500 characters."));
        }
        return value;
    }

    private void fail(List<ApiFieldViolation> violations) {
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
    }

    public record ValidatedReschedule(OffsetDateTime start, UUID employeeId, String reason) {
    }
}
