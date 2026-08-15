package com.bookflow.bookings;

import com.bookflow.bookings.api.CancelBookingRequest;
import com.bookflow.bookings.api.RescheduleBookingRequest;
import com.bookflow.bookings.application.BookingLifecycleValidator;
import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingLifecycleValidatorTest {
    private final BookingLifecycleValidator validator = new BookingLifecycleValidator();

    @Test
    void validatesRescheduleAndNormalizesOptionalReason() {
        RescheduleBookingRequest request = new RescheduleBookingRequest();
        request.setStart(OffsetDateTime.parse("2026-08-20T14:00:00+07:00"));
        request.setReason("  Customer requested  ");

        var result = validator.validate(request);

        assertThat(result.start()).isEqualTo(request.getStart());
        assertThat(result.reason()).isEqualTo("Customer requested");
    }

    @Test
    void rejectsMissingStartAndOversizedReason() {
        RescheduleBookingRequest request = new RescheduleBookingRequest();
        request.setReason("x".repeat(501));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void cancelBodyIsOptionalAndReasonIsBounded() {
        assertThat(validator.cancelReason(null)).isNull();
        CancelBookingRequest request = new CancelBookingRequest();
        request.setReason("x".repeat(501));
        assertThatThrownBy(() -> validator.cancelReason(request))
                .isInstanceOf(RequestValidationException.class);
    }
}
