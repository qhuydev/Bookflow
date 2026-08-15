package com.bookflow.bookings;

import com.bookflow.BookFlowApplication;
import com.bookflow.bookings.api.CancelBookingRequest;
import com.bookflow.bookings.api.RescheduleBookingRequest;
import com.bookflow.bookings.application.BookingConflictException;
import com.bookflow.bookings.application.BookingExpiryService;
import com.bookflow.bookings.application.BookingLifecycleService;
import com.bookflow.bookings.application.BookingPersistenceService;
import com.bookflow.bookings.application.SlotUnavailableException;
import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingCustomer;
import com.bookflow.bookings.domain.BookingItemSnapshot;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.businesses.authorization.TenantPermissionDeniedException;
import com.bookflow.schedules.availability.AvailabilityQueryService;
import com.bookflow.shared.error.ResourceNotFoundException;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "bookflow.booking.expiry-enabled=false",
                "bookflow.booking.expiry-batch-size=2",
                "bookflow.availability.slot-step-minutes=15",
                "bookflow.availability.default-lead-time-minutes=0"
        }
)
@ActiveProfiles("testcontainers")
@Import({PostgresTestcontainerConfiguration.class, BookingLifecycleIT.FixedClockConfiguration.class})
class BookingLifecycleIT {
    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Autowired JdbcTemplate jdbc;
    @Autowired BookingPersistenceService persistence;
    @Autowired BookingExpiryService expiry;
    @Autowired BookingLifecycleService lifecycle;
    @Autowired AvailabilityQueryService availability;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void clear() {
        jdbc.execute("DROP TRIGGER IF EXISTS bf046_fail_history ON booking_status_history");
        jdbc.execute("DROP FUNCTION IF EXISTS bf046_fail_history()");
        jdbc.execute("DROP TRIGGER IF EXISTS bf046_fail_reschedule ON booking_reschedule_history");
        jdbc.execute("DROP FUNCTION IF EXISTS bf046_fail_reschedule()");
        jdbc.execute("""
                TRUNCATE TABLE booking_reschedule_history, booking_idempotency_keys,
                    booking_status_history, booking_items, bookings,
                    schedule_breaks, schedule_exceptions, working_schedule_rules,
                    employee_services, branch_services, employee_branch_assignments,
                    employees, services, branches, business_memberships, businesses,
                    refresh_tokens, auth_sessions, users CASCADE
                """);
    }

    @Test
    void expiryProcessesBoundedBatchesOnceAndReleasesAvailability() throws Exception {
        Fixture fixture = fixture("expiry", "OWNER");
        Booking nine = booking(fixture, "2026-08-20T09:00:00Z", NOW.minusSeconds(1200));
        booking(fixture, "2026-08-20T11:00:00Z", NOW.minusSeconds(1200));
        booking(fixture, "2026-08-20T14:00:00Z", NOW.minusSeconds(1200));
        Booking future = booking(fixture, "2026-08-21T09:00:00Z", NOW);
        Booking confirmed = booking(fixture, "2026-08-20T16:00:00Z", NOW.minusSeconds(1200));
        persistence.transition(fixture.business(), confirmed.id(), BookingStatus.CONFIRMED,
                fixture.owner(), "Confirmed");

        assertThat(hasSlot(fixture, null, "2026-08-20T09:00:00Z")).isFalse();
        assertThat(expiry.expireDueBookings()).isEqualTo(3);

        assertThat(bookingStatus(nine.id())).isEqualTo("EXPIRED");
        assertThat(bookingStatus(future.id())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(bookingStatus(confirmed.id())).isEqualTo("CONFIRMED");
        assertThat(historyCount(nine.id(), "EXPIRED")).isOne();
        assertThat(hasSlot(fixture, null, "2026-08-20T09:00:00Z")).isTrue();
    }

    @Test
    void twoExpiryWorkersProduceOneTransitionAndHistory() throws Exception {
        Fixture fixture = fixture("expiry-race", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW.minusSeconds(1200));
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> results = runConcurrently(List.of(
                () -> { start.await(); return expiry.expireDueBookings(); },
                () -> { start.await(); return expiry.expireDueBookings(); }
        ), start);

        assertThat(results).hasSize(2).allMatch(value -> value == 0 || value == 1);
        assertThat(results).hasSameSizeAs(results);
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(bookingStatus(booking.id())).isEqualTo("EXPIRED");
        assertThat(historyCount(booking.id(), "EXPIRED")).isOne();
    }

    @Test
    void expiryHistoryFailureRollsBackStatus() {
        Fixture fixture = fixture("expiry-rollback", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW.minusSeconds(1200));
        jdbc.execute("""
                CREATE FUNCTION bf046_fail_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'bf046 integration rollback probe'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER bf046_fail_history BEFORE INSERT ON booking_status_history
                FOR EACH ROW WHEN (NEW.to_status = 'EXPIRED') EXECUTE FUNCTION bf046_fail_history()
                """);

        assertThatThrownBy(() -> expiry.expireDueBookings()).isInstanceOf(RuntimeException.class);
        assertThat(bookingStatus(booking.id())).isEqualTo("PENDING_CONFIRMATION");
        assertThat(historyCount(booking.id(), "EXPIRED")).isZero();
    }

    @Test
    void customerAndBusinessCancelAreScopedAuthorizedAtomicAndReleaseSlot() {
        Fixture fixture = fixture("cancel", "OWNER");
        Booking customerBooking = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        lifecycle.cancelAsCustomer(fixture.owner(), customerBooking.id(), reason("Customer request"));
        assertThat(bookingStatus(customerBooking.id())).isEqualTo("CANCELLED_BY_CUSTOMER");
        assertThat(historyCount(customerBooking.id(), "CANCELLED_BY_CUSTOMER")).isOne();
        assertThat(hasSlot(fixture, null, "2026-08-20T09:00:00Z")).isTrue();

        Booking businessBooking = booking(fixture, "2026-08-20T11:00:00Z", NOW);
        lifecycle.cancelAsBusiness(fixture.owner(), fixture.business(), businessBooking.id(), null);
        assertThat(bookingStatus(businessBooking.id())).isEqualTo("CANCELLED_BY_BUSINESS");

        assertThatThrownBy(() -> lifecycle.cancelAsCustomer(
                UUID.randomUUID(), businessBooking.id(), null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.cancelAsBusiness(
                fixture.staff(), fixture.business(), businessBooking.id(), null))
                .isInstanceOf(TenantPermissionDeniedException.class);
        assertThatThrownBy(() -> lifecycle.cancelAsBusiness(
                fixture.owner(), UUID.randomUUID(), businessBooking.id(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rescheduleIsAtomicExcludesSelfAndPreservesSnapshots() {
        Fixture fixture = fixture("reschedule", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        jdbc.update("UPDATE services SET name='Changed', price=999999, duration_minutes=30 WHERE id=?",
                fixture.service());

        var response = lifecycle.rescheduleAsBusiness(
                fixture.owner(), fixture.business(), booking.id(), reschedule("2026-08-20T09:15:00Z")
        );

        assertThat(response.start().toInstant()).isEqualTo(Instant.parse("2026-08-20T09:15:00Z"));
        assertThat(response.end().toInstant()).isEqualTo(Instant.parse("2026-08-20T10:15:00Z"));
        assertThat(response.items().getFirst().name()).isEqualTo("Service reschedule");
        assertThat(response.items().getFirst().price()).isEqualByComparingTo("100000.00");
        assertThat(response.items().getFirst().durationMinutes()).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT start_at FROM bookings WHERE id=?", java.sql.Timestamp.class,
                booking.id()).toInstant()).isEqualTo(Instant.parse("2026-08-20T09:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_reschedule_history WHERE booking_id=?",
                Integer.class, booking.id())).isOne();
    }

    @Test
    void failedRescheduleKeepsOldRangeAndBothBookingsIntact() {
        Fixture fixture = fixture("reschedule-conflict", "OWNER");
        Booking original = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        Booking target = booking(fixture, "2026-08-20T14:00:00Z", NOW);

        assertThatThrownBy(() -> lifecycle.rescheduleAsBusiness(
                fixture.owner(), fixture.business(), original.id(), reschedule("2026-08-20T14:00:00Z")
        )).isInstanceOf(SlotUnavailableException.class);

        assertRange(original.id(), "2026-08-20T08:45:00Z", "2026-08-20T10:10:00Z");
        assertRange(target.id(), "2026-08-20T13:45:00Z", "2026-08-20T15:10:00Z");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_reschedule_history WHERE booking_id=?",
                Integer.class, original.id())).isZero();
    }

    @Test
    void rescheduleHonorsBreaksAndRollsBackWhenAuditInsertFails() {
        Fixture fixture = fixture("reschedule-rules", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        UUID rule = jdbc.queryForObject("""
                SELECT id FROM working_schedule_rules WHERE tenant_id=? AND employee_id=?
                """, UUID.class, fixture.business(), fixture.employee());
        jdbc.update("""
                INSERT INTO schedule_breaks (id,tenant_id,schedule_rule_id,start_local_time,end_local_time)
                VALUES (?,?,?,'14:00','15:30')
                """, UUID.randomUUID(), fixture.business(), rule);

        assertThatThrownBy(() -> lifecycle.rescheduleAsBusiness(
                fixture.owner(), fixture.business(), booking.id(), reschedule("2026-08-20T14:00:00Z")
        )).isInstanceOf(SlotUnavailableException.class);
        assertRange(booking.id(), "2026-08-20T08:45:00Z", "2026-08-20T10:10:00Z");

        jdbc.update("DELETE FROM schedule_breaks WHERE tenant_id=?", fixture.business());
        jdbc.execute("""
                CREATE FUNCTION bf046_fail_reschedule() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'bf046 reschedule rollback probe'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER bf046_fail_reschedule BEFORE INSERT ON booking_reschedule_history
                FOR EACH ROW EXECUTE FUNCTION bf046_fail_reschedule()
                """);

        assertThatThrownBy(() -> lifecycle.rescheduleAsBusiness(
                fixture.owner(), fixture.business(), booking.id(), reschedule("2026-08-20T14:00:00Z")
        )).isInstanceOf(RuntimeException.class);
        assertRange(booking.id(), "2026-08-20T08:45:00Z", "2026-08-20T10:10:00Z");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_reschedule_history",
                Integer.class)).isZero();
    }

    @Test
    void concurrentReschedulesSameTargetAllowOneAndPreserveLoser() throws Exception {
        Fixture fixture = fixture("reschedule-race", "OWNER");
        Booking first = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        Booking second = booking(fixture, "2026-08-20T11:00:00Z", NOW);
        CountDownLatch start = new CountDownLatch(1);
        List<Outcome> results = runConcurrently(List.of(
                () -> outcome(start, () -> lifecycle.rescheduleAsBusiness(
                        fixture.owner(), fixture.business(), first.id(), reschedule("2026-08-20T14:00:00Z"))),
                () -> outcome(start, () -> lifecycle.rescheduleAsBusiness(
                        fixture.owner(), fixture.business(), second.id(), reschedule("2026-08-20T14:00:00Z")))
        ), start);

        assertThat(results).filteredOn(Outcome::success).hasSize(1);
        assertThat(results).filteredOn(result -> !result.success()).hasSize(1)
                .allMatch(result -> result.error() instanceof SlotUnavailableException
                        || result.error() instanceof BookingConflictException);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bookings
                WHERE tenant_id=? AND start_at='2026-08-20T13:45:00Z'::timestamptz
                """, Integer.class, fixture.business())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_reschedule_history",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bookings
                WHERE tenant_id=? AND start_at IN (
                    '2026-08-20T08:45:00Z'::timestamptz,
                    '2026-08-20T10:45:00Z'::timestamptz
                )
                """, Integer.class, fixture.business())).isOne();
    }

    @Test
    void cancelAndExpiryRaceProduceOneTerminalTransition() throws Exception {
        Fixture fixture = fixture("cancel-expiry-race", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW.minusSeconds(1200));
        CountDownLatch start = new CountDownLatch(1);
        List<Outcome> results = runConcurrently(List.of(
                () -> outcome(start, () -> { expiry.expireDueBookings(); return null; }),
                () -> outcome(start, () -> { lifecycle.cancelAsCustomer(
                        fixture.owner(), booking.id(), null); return null; })
        ), start);

        assertThat(results).filteredOn(Outcome::success).isNotEmpty();
        assertThat(bookingStatus(booking.id())).isIn("EXPIRED", "CANCELLED_BY_CUSTOMER");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_status_history
                WHERE booking_id=? AND from_status IS NOT NULL
                """, Integer.class, booking.id())).isOne();
    }

    @Test
    void rescheduleAndCancelRaceCannotLeaveRangeAndStatusInconsistent() throws Exception {
        Fixture fixture = fixture("reschedule-cancel-race", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        CountDownLatch start = new CountDownLatch(1);
        List<Outcome> results = runConcurrently(List.of(
                () -> outcome(start, () -> lifecycle.rescheduleAsBusiness(
                        fixture.owner(), fixture.business(), booking.id(), reschedule("2026-08-20T14:00:00Z"))),
                () -> outcome(start, () -> { lifecycle.cancelAsBusiness(
                        fixture.owner(), fixture.business(), booking.id(), null); return null; })
        ), start);

        assertThat(results).filteredOn(Outcome::success).hasSize(1);
        String state = bookingStatus(booking.id());
        int audits = jdbc.queryForObject("SELECT COUNT(*) FROM booking_reschedule_history WHERE booking_id=?",
                Integer.class, booking.id());
        int terminalHistory = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_status_history
                WHERE booking_id=? AND to_status='CANCELLED_BY_BUSINESS'
                """, Integer.class, booking.id());
        assertThat((audits == 1 && terminalHistory == 0 && state.equals("PENDING_CONFIRMATION"))
                || (audits == 0 && terminalHistory == 1 && state.equals("CANCELLED_BY_BUSINESS"))).isTrue();
    }

    @Test
    void endpointsRequireJwtAndCsrfAndOpenApiDocumentsLifecycle() throws Exception {
        Fixture fixture = fixture("api", "OWNER");
        Booking booking = booking(fixture, "2026-08-20T09:00:00Z", NOW);
        String path = "/api/v1/businesses/%s/bookings/%s/cancel"
                .formatted(fixture.business(), booking.id());
        MvcResult csrfResponse = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(csrfResponse.getResponse().getContentAsString(), "$.token");

        mvc.perform(post(path)
                        .cookie(csrfResponse.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path)
                        .cookie(csrfResponse.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path)
                        .cookie(csrfResponse.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .with(jwt().jwt(jwt -> jwt.subject(fixture.owner().toString()))))
                .andExpect(status().isNoContent());

        String docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(docs,
                "$.paths['/api/v1/businesses/{businessId}/bookings/{bookingId}/reschedule'].post").toString())
                .contains("200", "400", "401", "403", "404", "409", "bearerAuth");
    }

    private boolean hasSlot(Fixture fixture, UUID excludedBookingId, String start) {
        return availability.availability(
                        fixture.slug(), fixture.branch(), fixture.service(), fixture.employee(), DATE,
                        excludedBookingId
                ).slots().stream().anyMatch(slot -> slot.start().toInstant().equals(Instant.parse(start)));
    }

    private Booking booking(Fixture fixture, String visibleStart, Instant createdAt) {
        Instant visible = Instant.parse(visibleStart);
        Booking booking = Booking.create(
                UUID.randomUUID(), fixture.business(), fixture.branch(), fixture.employee(),
                new BookingCustomer(fixture.owner(), "Customer", "customer@example.test", null),
                visible.minus(Duration.ofMinutes(15)), visible.plus(Duration.ofMinutes(70)),
                List.of(new BookingItemSnapshot(
                        fixture.service(), "Service " + fixture.suffix(), new BigDecimal("100000.00"),
                        "VND", 60, 15, 10
                )), Duration.ofMinutes(10), Clock.fixed(createdAt, ZoneOffset.UTC)
        );
        return persistence.create(booking);
    }

    private Fixture fixture(String suffix, String ownerRole) {
        UUID owner = user(suffix + "-owner");
        UUID staff = user(suffix + "-staff");
        UUID business = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        String slug = suffix + "-" + business.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO businesses (id,name,slug,business_type,time_zone,status,max_booking_advance_days)
                VALUES (?, ?, ?, 'SALON', 'UTC', 'ACTIVE', 90)
                """, business, "Business " + suffix, slug);
        membership(business, owner, ownerRole);
        membership(business, staff, "STAFF");
        jdbc.update("""
                INSERT INTO branches (id,tenant_id,code,name,address_line1,city,country_code,time_zone,status)
                VALUES (?,? ,?,?,'1 Main','City','VN','UTC','ACTIVE')
                """, branch, business, code("B", branch), "Branch " + suffix);
        jdbc.update("""
                INSERT INTO employees (id,tenant_id,code,full_name,status)
                VALUES (?,?,?,?, 'ACTIVE')
                """, employee, business, code("E", employee), "Employee " + suffix);
        jdbc.update("""
                INSERT INTO services (id,tenant_id,name,price,currency,duration_minutes,
                    buffer_before_minutes,buffer_after_minutes,status)
                VALUES (?,?,?,100000,'VND',60,15,10,'ACTIVE')
                """, service, business, "Service " + suffix);
        jdbc.update("INSERT INTO branch_services (tenant_id,branch_id,service_id) VALUES (?,?,?)",
                business, branch, service);
        jdbc.update("INSERT INTO employee_branch_assignments (tenant_id,employee_id,branch_id) VALUES (?,?,?)",
                business, employee, branch);
        jdbc.update("INSERT INTO employee_services (tenant_id,employee_id,service_id) VALUES (?,?,?)",
                business, employee, service);
        jdbc.update("""
                INSERT INTO working_schedule_rules (id,tenant_id,branch_id,employee_id,weekday,
                    start_local_time,end_local_time,effective_from)
                VALUES (?,?,?,?,?,'08:00','18:00',?)
                """, UUID.randomUUID(), business, branch, employee, DATE.getDayOfWeek().name(), DATE.minusDays(1));
        return new Fixture(suffix, slug, business, owner, staff, branch, employee, service);
    }

    private UUID user(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,normalized_email,password_hash,status) VALUES (?,?,?,'ACTIVE')",
                id, prefix + "-" + id + "@example.test", "$argon2id$bf046-test-only");
        return id;
    }

    private void membership(UUID business, UUID user, String role) {
        jdbc.update("""
                INSERT INTO business_memberships (id,tenant_id,user_id,role,status)
                VALUES (?,?,?,?,'ACTIVE')
                """, UUID.randomUUID(), business, user, role);
    }

    private CancelBookingRequest reason(String reason) {
        CancelBookingRequest request = new CancelBookingRequest();
        request.setReason(reason);
        return request;
    }

    private RescheduleBookingRequest reschedule(String start) {
        RescheduleBookingRequest request = new RescheduleBookingRequest();
        request.setStart(OffsetDateTime.parse(start));
        request.setReason("Rescheduled in BF-046 test");
        return request;
    }

    private String bookingStatus(UUID bookingId) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id=?", String.class, bookingId);
    }

    private int historyCount(UUID bookingId, String status) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_status_history WHERE booking_id=? AND to_status=?
                """, Integer.class, bookingId, status);
    }

    private void assertRange(UUID bookingId, String start, String end) {
        var values = jdbc.queryForMap("SELECT start_at,end_at FROM bookings WHERE id=?", bookingId);
        assertThat(((java.sql.Timestamp) values.get("start_at")).toInstant()).isEqualTo(Instant.parse(start));
        assertThat(((java.sql.Timestamp) values.get("end_at")).toInstant()).isEqualTo(Instant.parse(end));
    }

    private String code(String prefix, UUID id) {
        return (prefix + id.toString().replace("-", "").substring(0, 10)).toUpperCase();
    }

    private Outcome outcome(CountDownLatch start, ThrowingSupplier supplier) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent start timed out.");
            }
            supplier.get();
            return new Outcome(true, null);
        } catch (Throwable error) {
            return new Outcome(false, error);
        }
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks, CountDownLatch start) throws Exception {
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return List.copyOf(results);
        }
    }

    private record Fixture(String suffix, String slug, UUID business, UUID owner, UUID staff,
                           UUID branch, UUID employee, UUID service) {
    }

    private record Outcome(boolean success, Throwable error) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock bookingLifecycleClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
