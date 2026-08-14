package com.bookflow.bookings;

import com.bookflow.bookings.api.CreateBookingCustomerRequest;
import com.bookflow.bookings.api.CreateBookingRequest;
import com.bookflow.bookings.application.BookingRequestFingerprint;
import com.bookflow.bookings.application.BookingRequestValidator;
import com.bookflow.shared.error.RequestValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRequestValidatorTest {
    private final BookingRequestValidator validator = new BookingRequestValidator();
    private final BookingRequestFingerprint fingerprint = new BookingRequestFingerprint();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void normalizesSemanticFieldsAndProducesStableFingerprint() {
        CreateBookingRequest first = request("  Khách Hàng  ", "  Demo@Example.TEST ", null,
                OffsetDateTime.parse("2026-08-20T09:00:00+07:00"));
        CreateBookingRequest second = request("Khách Hàng", "demo@example.test", null,
                OffsetDateTime.parse("2026-08-20T02:00:00Z"));

        var validatedFirst = validator.validate("  AN-NHIEN  ", "booking-key-0001", first);
        var validatedSecond = validator.validate("an-nhien", "booking-key-0002", second);

        assertThat(validatedFirst.slug()).isEqualTo("an-nhien");
        assertThat(validatedFirst.customer().name()).isEqualTo("Khách Hàng");
        assertThat(validatedFirst.customer().email()).isEqualTo("demo@example.test");
        assertThat(fingerprint.fingerprint(validatedFirst))
                .isEqualTo(fingerprint.fingerprint(validatedSecond));
    }

    @Test
    void rejectsMissingContactUnsafeIdempotencyKeyAndClientControlledFields() throws Exception {
        CreateBookingRequest request = objectMapper.readValue("""
                {"branchId":"00000000-0000-0000-0000-000000000001","serviceId":"00000000-0000-0000-0000-000000000002","employeeId":"00000000-0000-0000-0000-000000000003","start":"2026-08-20T09:00:00Z","price":1,"customer":{"name":"Guest"}}
                """, CreateBookingRequest.class);

        assertThatThrownBy(() -> validator.validate("business", "bad key", request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(exception -> assertThat(((RequestValidationException) exception).violations())
                        .extracting(violation -> violation.field())
                        .contains("Idempotency-Key", "price", "customer"));
    }

    @Test
    void rejectsUnknownCustomerFieldsAndMissingRequiredResources() throws Exception {
        CreateBookingRequest request = objectMapper.readValue("""
                {"customer":{"name":"Guest","email":"guest@example.test","userId":"00000000-0000-0000-0000-000000000004"}}
                """, CreateBookingRequest.class);

        assertThatThrownBy(() -> validator.validate("business", "booking-key-0003", request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(exception -> assertThat(((RequestValidationException) exception).violations())
                        .extracting(violation -> violation.field())
                        .contains("branchId", "serviceId", "employeeId", "start", "customer.userId"));
    }

    private CreateBookingRequest request(
            String name,
            String email,
            String phone,
            OffsetDateTime start
    ) {
        CreateBookingCustomerRequest customer = new CreateBookingCustomerRequest();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setBranchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        request.setServiceId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        request.setEmployeeId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        request.setStart(start);
        request.setCustomer(customer);
        return request;
    }
}
