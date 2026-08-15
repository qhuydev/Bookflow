package com.bookflow.bookings;

import com.bookflow.BookFlowApplication;
import com.bookflow.bookings.api.CreateBookingCustomerRequest;
import com.bookflow.bookings.api.CreateBookingRequest;
import com.bookflow.bookings.application.BookingCreationService;
import com.bookflow.bookings.application.IdempotencyKeyReusedException;
import com.bookflow.bookings.application.SlotUnavailableException;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BookFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "bookflow.availability.slot-step-minutes=15",
                "bookflow.availability.default-lead-time-minutes=60",
                "bookflow.booking.hold-minutes=10",
                "bookflow.authentication.cors.allowed-origins[0]=http://127.0.0.1:3001"
        }
)
@ActiveProfiles("testcontainers")
@Import({PostgresTestcontainerConfiguration.class, CreateBookingIT.FixedClockConfiguration.class})
class CreateBookingIT {
    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");
    private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 8, 20);
    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-08-20T09:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingCreationService bookingService;
    @Autowired AvailabilityQueryService availabilityService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void clear() {
        jdbc.execute("""
                TRUNCATE TABLE booking_idempotency_keys, booking_status_history, booking_items, bookings,
                    schedule_breaks, schedule_exceptions, working_schedule_rules,
                    employee_services, branch_services, employee_branch_assignments,
                    employees, services, branches, business_memberships, businesses,
                    refresh_tokens, auth_sessions, users CASCADE
                """);
    }

    @Test
    void publicCreateRequiresCsrfCalculatesSnapshotsAndReplaysIdempotently() throws Exception {
        Fixture fixture = fixture(
                "public-create", 60, 15, 10, 90,
                BOOKING_DATE, "08:00", "12:00"
        );
        String body = body(fixture, fixture.firstEmployee(), NINE, "Guest", " Guest@Example.TEST ");
        String path = path(fixture);

        mvc.perform(post(path).header("Idempotency-Key", "booking-public-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        MvcResult created = performCreate(path, "booking-public-0001", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.start").value("2026-08-20T09:00:00Z"))
                .andExpect(jsonPath("$.end").value("2026-08-20T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-14T10:10:00Z"))
                .andExpect(jsonPath("$.totalAmount").value(100000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.items[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.items[0].bufferBeforeMinutes").value(15))
                .andExpect(jsonPath("$.items[0].bufferAfterMinutes").value(10))
                .andReturn();
        String bookingId = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingId");
        assertThat(created.getResponse().getContentAsString())
                .doesNotContain("tenantId", "history", "idempotency", "customer_email");
        assertThat(jdbc.queryForObject(
                "SELECT customer_email FROM bookings WHERE id = ?", String.class, UUID.fromString(bookingId)))
                .isEqualTo("guest@example.test");
        assertThat(jdbc.queryForObject(
                "SELECT start_at FROM bookings WHERE id = ?", java.sql.Timestamp.class, UUID.fromString(bookingId))
                .toInstant()).isEqualTo(Instant.parse("2026-08-20T08:45:00Z"));
        assertThat(jdbc.queryForObject(
                "SELECT end_at FROM bookings WHERE id = ?", java.sql.Timestamp.class, UUID.fromString(bookingId))
                .toInstant()).isEqualTo(Instant.parse("2026-08-20T10:10:00Z"));

        performCreate(path, "booking-public-other-key", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        performCreate(path, "booking-public-0001", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId));

        String changedCustomer = body(fixture, fixture.firstEmployee(), NINE, "Another Guest", "guest@example.test");
        performCreate(path, "booking-public-0001", changedCustomer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("booking_idempotency_keys")).isEqualTo(1);

        String compactBody = body.strip();
        String clientControlled = compactBody.substring(0, compactBody.length() - 1)
                + ",\"price\":1,\"status\":\"CONFIRMED\"}";
        performCreate(path, "booking-public-0002", clientControlled)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidResourcesSchedulesBreaksTimeOffLeadAndHorizonLeaveNoOrphans() {
        Fixture base = fixture("invalid-base", 60, 0, 0, 90);
        assertThatThrownBy(() -> bookingService.create(base.slug(), "booking-invalid-0001",
                request(base, UUID.randomUUID(), NINE, "Guest")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertUnavailable(base, "booking-invalid-0002", NINE.minusHours(1));

        Fixture breakFixture = fixture("invalid-break", 60, 0, 0, 90);
        UUID rule = ruleId(breakFixture, breakFixture.firstEmployee());
        jdbc.update("""
                INSERT INTO schedule_breaks (
                    id, tenant_id, schedule_rule_id, start_local_time, end_local_time
                ) VALUES (?, ?, ?, '10:00', '11:00')
                """, UUID.randomUUID(), breakFixture.business(), rule);
        assertUnavailable(breakFixture, "booking-invalid-0003", NINE.plusHours(1));

        Fixture timeOff = fixture("invalid-timeoff", 60, 0, 0, 90);
        jdbc.update("""
                INSERT INTO schedule_exceptions (
                    id, tenant_id, branch_id, employee_id, exception_date, type,
                    start_local_time, end_local_time
                ) VALUES (?, ?, ?, ?, ?, 'TIME_OFF', '11:00', '12:00')
                """, UUID.randomUUID(), timeOff.business(), timeOff.branch(),
                timeOff.firstEmployee(), BOOKING_DATE);
        assertUnavailable(timeOff, "booking-invalid-0004", NINE.plusHours(2));

        Fixture horizon = fixture("invalid-horizon", 60, 0, 0, 1);
        assertUnavailable(horizon, "booking-invalid-0005", NINE);

        Fixture lead = fixture("invalid-lead", 30, 0, 0, 90, LocalDate.of(2026, 8, 14), "10:00", "13:00");
        assertUnavailable(lead, "booking-invalid-0006", OffsetDateTime.parse("2026-08-14T10:30:00Z"));

        Fixture foreign = fixture("invalid-foreign", 60, 0, 0, 90);
        assertThatThrownBy(() -> bookingService.create(base.slug(), "booking-invalid-0007",
                request(base, foreign.firstEmployee(), NINE, "Guest")))
                .isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("UPDATE employees SET status = 'ARCHIVED' WHERE id = ?", base.firstEmployee());
        assertThatThrownBy(() -> bookingService.create(base.slug(), "booking-invalid-0008",
                request(base, base.firstEmployee(), NINE, "Guest")))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(count("bookings")).isZero();
        assertThat(count("booking_idempotency_keys")).isZero();
    }

    @Test
    void activeBookingsAreBatchLoadedIntoPublicAvailabilityPerEmployee() {
        Fixture fixture = fixture("busy-provider", 60, 0, 0, 90);
        UUID secondEmployee = addEmployee(fixture, "EMP-B", BOOKING_DATE, "09:00", "12:00");

        assertThat(employeeIdsAt(fixture, NINE)).containsExactlyInAnyOrder(
                fixture.firstEmployee(), secondEmployee);
        bookingService.create(fixture.slug(), "booking-busy-0001",
                request(fixture, fixture.firstEmployee(), NINE, "Guest A"));
        assertThat(employeeIdsAt(fixture, NINE)).containsExactly(secondEmployee);
        bookingService.create(fixture.slug(), "booking-busy-0002",
                request(fixture, secondEmployee, NINE, "Guest B"));
        assertThat(employeeIdsAt(fixture, NINE)).isEmpty();

        jdbc.update("UPDATE bookings SET status = 'CANCELLED_BY_CUSTOMER' WHERE employee_id = ?",
                fixture.firstEmployee());
        assertThat(employeeIdsAt(fixture, NINE)).containsExactly(fixture.firstEmployee());
    }

    @Test
    void publicCreateMayOmitEmployeeAndReturnsTheActualAssignment() throws Exception {
        Fixture fixture = fixture("public-auto", 60, 0, 0, 90);
        UUID secondEmployee = addEmployee(fixture, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        UUID expected = firstEmployeeByDatabaseOrder(fixture);
        String body = body(fixture, null, NINE, "Auto Guest", "auto@example.test");

        performCreate(path(fixture), "booking-public-auto-0001", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value(expected.toString()));
        assertThat(jdbc.queryForObject(
                "SELECT employee_id FROM bookings", UUID.class)).isEqualTo(expected);
    }

    @Test
    void twentyConcurrentRequestsForOneSlotProduceOneBookingAndNineteenConflicts() throws Exception {
        Fixture fixture = fixture("concurrent-slot", 60, 0, 0, 90);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int index = 0; index < 20; index++) {
            String key = "booking-race-" + String.format("%04d", index);
            tasks.add(() -> outcome(start, () -> bookingService.create(
                    fixture.slug(), key, request(fixture, fixture.firstEmployee(), NINE, "Race Guest"))));
        }

        List<Outcome> outcomes = runConcurrently(tasks, start);
        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() instanceof SlotUnavailableException)
                .hasSize(19);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("booking_items")).isEqualTo(1);
        assertThat(count("booking_status_history")).isEqualTo(1);
        assertThat(count("booking_idempotency_keys")).isEqualTo(1);
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsOneBookingToEveryCaller() throws Exception {
        Fixture fixture = fixture("concurrent-idempotency", 60, 0, 0, 90);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int index = 0; index < 20; index++) {
            tasks.add(() -> outcome(start, () -> bookingService.create(
                    fixture.slug(), "booking-same-key-0001",
                    request(fixture, fixture.firstEmployee(), NINE, "Same Guest"))));
        }

        List<Outcome> outcomes = runConcurrently(tasks, start);
        assertThat(outcomes).allMatch(Outcome::success);
        assertThat(outcomes).extracting(Outcome::bookingId).doesNotContainNull().containsOnly(
                outcomes.getFirst().bookingId());
        assertThat(outcomes).filteredOn(Outcome::replayed).hasSize(19);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("booking_idempotency_keys")).isEqualTo(1);
    }

    @Test
    void omittedEmployeeUsesDeterministicLoadOrderAndReplaysTheAssignedEmployee() {
        Fixture fixture = fixture("auto-order", 60, 0, 0, 90);
        UUID secondEmployee = addEmployee(fixture, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        UUID expectedFirst = firstEmployeeByDatabaseOrder(fixture);

        var first = bookingService.create(fixture.slug(), "booking-auto-order-0001",
                request(fixture, null, NINE, "Auto Guest"));
        assertThat(first.response().employeeId()).isEqualTo(expectedFirst);

        var replay = bookingService.create(fixture.slug(), "booking-auto-order-0001",
                request(fixture, null, NINE, "Auto Guest"));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.response().bookingId()).isEqualTo(first.response().bookingId());
        assertThat(replay.response().employeeId()).isEqualTo(expectedFirst);

        assertThatThrownBy(() -> bookingService.create(fixture.slug(), "booking-auto-order-0001",
                request(fixture, null, NINE.plusHours(1), "Auto Guest")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("booking_idempotency_keys")).isEqualTo(1);
    }

    @Test
    void autoAssignmentSkipsUnavailableAndInactiveCandidatesThenFailsWhenAllAreBusy() {
        Fixture fixture = fixture("auto-skip", 60, 0, 0, 90);
        UUID secondEmployee = addEmployee(fixture, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        UUID preferred = firstEmployeeByDatabaseOrder(fixture);
        UUID fallback = preferred.equals(fixture.firstEmployee()) ? secondEmployee : fixture.firstEmployee();

        bookingService.create(fixture.slug(), "booking-auto-skip-0001",
                request(fixture, preferred, NINE, "Occupy Preferred"));
        var fallbackResult = bookingService.create(fixture.slug(), "booking-auto-skip-0002",
                request(fixture, null, NINE, "Fallback Guest"));
        assertThat(fallbackResult.response().employeeId()).isEqualTo(fallback);

        assertThatThrownBy(() -> bookingService.create(fixture.slug(), "booking-auto-skip-0003",
                request(fixture, null, NINE, "No Candidate")))
                .isInstanceOf(SlotUnavailableException.class);
        assertThat(count("bookings")).isEqualTo(2);
        assertThat(count("booking_idempotency_keys")).isEqualTo(2);

        Fixture inactive = fixture("auto-inactive", 60, 0, 0, 90);
        UUID active = addEmployee(inactive, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        jdbc.update("UPDATE employees SET status = 'ARCHIVED' WHERE id = ?", inactive.firstEmployee());
        var activeResult = bookingService.create(inactive.slug(), "booking-auto-inactive-0001",
                request(inactive, null, NINE, "Active Only"));
        assertThat(activeResult.response().employeeId()).isEqualTo(active);
    }

    @Test
    void concurrentAutoAssignmentFallsBackAcrossCandidatesWithoutOrphans() throws Exception {
        Fixture fixture = fixture("auto-race", 60, 0, 0, 90);
        addEmployee(fixture, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Outcome>> tasks = List.of(
                () -> outcome(start, () -> bookingService.create(
                        fixture.slug(), "booking-auto-race-0001",
                        request(fixture, null, NINE, "Race A"))),
                () -> outcome(start, () -> bookingService.create(
                        fixture.slug(), "booking-auto-race-0002",
                        request(fixture, null, NINE, "Race B")))
        );

        List<Outcome> outcomes = runConcurrently(tasks, start);
        assertThat(outcomes).allMatch(Outcome::success);
        List<UUID> assigned = outcomes.stream().map(Outcome::bookingId)
                .map(id -> jdbc.queryForObject("SELECT employee_id FROM bookings WHERE id = ?", UUID.class, id))
                .toList();
        assertThat(assigned).doesNotHaveDuplicates().hasSize(2);
        assertThat(count("bookings")).isEqualTo(2);
        assertThat(count("booking_items")).isEqualTo(2);
        assertThat(count("booking_status_history")).isEqualTo(2);
        assertThat(count("booking_idempotency_keys")).isEqualTo(2);
    }

    @Test
    void halfOpenBoundariesDifferentEmployeesAndBuffersUseOccupiedRangeSemantics() {
        Fixture boundary = fixture("boundary", 60, 0, 0, 90);
        UUID secondEmployee = addEmployee(boundary, "EMP-B", BOOKING_DATE, "09:00", "12:00");
        bookingService.create(boundary.slug(), "booking-boundary-0001",
                request(boundary, boundary.firstEmployee(), NINE, "A 09"));
        bookingService.create(boundary.slug(), "booking-boundary-0002",
                request(boundary, boundary.firstEmployee(), NINE.plusHours(1), "A 10"));
        bookingService.create(boundary.slug(), "booking-boundary-0003",
                request(boundary, secondEmployee, NINE, "B 09"));
        assertThat(countWhere("bookings", "tenant_id", boundary.business())).isEqualTo(3);

        Fixture buffered = fixture("buffered", 60, 0, 15, 90);
        bookingService.create(buffered.slug(), "booking-buffer-0001",
                request(buffered, buffered.firstEmployee(), NINE, "Buffered"));
        assertThatThrownBy(() -> bookingService.create(buffered.slug(), "booking-buffer-0002",
                request(buffered, buffered.firstEmployee(), NINE.plusHours(1), "Too early")))
                .isInstanceOf(SlotUnavailableException.class);
        assertThat(jdbc.queryForObject(
                "SELECT end_at FROM bookings WHERE tenant_id = ?", java.sql.Timestamp.class, buffered.business())
                .toInstant()).isEqualTo(Instant.parse("2026-08-20T10:15:00Z"));
    }

    @Test
    void openApiDocumentsPublicBookingIdempotencyCsrfAndPublicSafeResponses() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String operation = JsonPath.read(
                document, "$.paths['/api/v1/public/businesses/{slug}/bookings'].post"
        ).toString();
        assertThat(operation)
                .contains("Idempotency-Key", "X-XSRF-TOKEN", "201", "200", "400", "403", "404", "409")
                .doesNotContain("tenantId", "requestFingerprint", "statusHistory");
    }

    @Test
    void corsPreflightAllowsTheBookingIdempotencyHeader() throws Exception {
        mvc.perform(options("/api/v1/public/businesses/demo/bookings")
                        .header("Origin", "http://127.0.0.1:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "content-type,x-xsrf-token,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3001"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            String path,
            String key,
            String body
    ) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        return mvc.perform(post(path)
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", JsonPath.<String>read(
                        csrf.getResponse().getContentAsString(), "$.token"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertUnavailable(Fixture fixture, String key, OffsetDateTime start) {
        assertThatThrownBy(() -> bookingService.create(
                fixture.slug(), key, request(fixture, fixture.firstEmployee(), start, "Guest")))
                .isInstanceOf(SlotUnavailableException.class);
    }

    private List<UUID> employeeIdsAt(Fixture fixture, OffsetDateTime start) {
        return availabilityService.availability(
                        fixture.slug(), fixture.branch(), fixture.service(), null,
                        start.toLocalDate())
                .slots().stream()
                .filter(slot -> slot.start().toInstant().equals(start.toInstant()))
                .findFirst()
                .map(slot -> slot.employeeIds())
                .orElse(List.of());
    }

    private Outcome outcome(CountDownLatch start, ThrowingSupplier supplier) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return Outcome.failure(new IllegalStateException("Concurrent start timed out."));
            }
            var result = supplier.get();
            return new Outcome(true, result.replayed(), result.response().bookingId(), null);
        } catch (Throwable error) {
            return Outcome.failure(error);
        }
    }

    private List<Outcome> runConcurrently(
            List<Callable<Outcome>> tasks,
            CountDownLatch start
    ) throws Exception {
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return List.copyOf(outcomes);
        }
    }

    private Fixture fixture(String suffix, int duration, int before, int after, int horizon) {
        return fixture(suffix, duration, before, after, horizon, BOOKING_DATE, "09:00", "12:00");
    }

    private Fixture fixture(
            String suffix,
            int duration,
            int before,
            int after,
            int horizon,
            LocalDate date,
            String scheduleStart,
            String scheduleEnd
    ) {
        UUID business = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        String slug = suffix + "-" + business.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO businesses (
                    id, name, slug, business_type, time_zone, status, max_booking_advance_days
                ) VALUES (?, ?, ?, 'SALON', 'UTC', 'ACTIVE', ?)
                """, business, "Business " + suffix, slug, horizon);
        jdbc.update("""
                INSERT INTO branches (
                    id, tenant_id, code, name, address_line1, city, country_code,
                    time_zone, status
                ) VALUES (?, ?, ?, ?, '1 Main', 'City', 'VN', 'UTC', 'ACTIVE')
                """, branch, business, code("B", branch), "Branch " + suffix);
        jdbc.update("""
                INSERT INTO services (
                    id, tenant_id, name, price, currency, duration_minutes,
                    buffer_before_minutes, buffer_after_minutes, status
                ) VALUES (?, ?, ?, ?, 'VND', ?, ?, ?, 'ACTIVE')
                """, service, business, "Service " + suffix,
                new BigDecimal("100000.00"), duration, before, after);
        jdbc.update("INSERT INTO branch_services (tenant_id, branch_id, service_id) VALUES (?, ?, ?)",
                business, branch, service);
        Fixture provisional = new Fixture(slug, business, branch, service, null);
        UUID employee = addEmployee(provisional, "EMP-A", date, scheduleStart, scheduleEnd);
        return new Fixture(slug, business, branch, service, employee);
    }

    private UUID addEmployee(
            Fixture fixture,
            String codePrefix,
            LocalDate date,
            String start,
            String end
    ) {
        UUID employee = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO employees (id, tenant_id, code, full_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, employee, fixture.business(), code(codePrefix, employee), "Employee " + codePrefix);
        jdbc.update("""
                INSERT INTO employee_branch_assignments (tenant_id, employee_id, branch_id)
                VALUES (?, ?, ?)
                """, fixture.business(), employee, fixture.branch());
        jdbc.update("""
                INSERT INTO employee_services (tenant_id, employee_id, service_id)
                VALUES (?, ?, ?)
                """, fixture.business(), employee, fixture.service());
        jdbc.update("""
                INSERT INTO working_schedule_rules (
                    id, tenant_id, branch_id, employee_id, weekday,
                    start_local_time, end_local_time, effective_from
                ) VALUES (?, ?, ?, ?, ?, ?::time, ?::time, ?)
                """, UUID.randomUUID(), fixture.business(), fixture.branch(), employee,
                date.getDayOfWeek().name(), start, end, date.minusDays(1));
        return employee;
    }

    private UUID ruleId(Fixture fixture, UUID employee) {
        return jdbc.queryForObject("""
                SELECT id FROM working_schedule_rules
                WHERE tenant_id = ? AND branch_id = ? AND employee_id = ?
                """, UUID.class, fixture.business(), fixture.branch(), employee);
    }

    private UUID firstEmployeeByDatabaseOrder(Fixture fixture) {
        return jdbc.queryForObject("""
                SELECT id FROM employees
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY id
                LIMIT 1
                """, UUID.class, fixture.business());
    }

    private CreateBookingRequest request(
            Fixture fixture,
            UUID employee,
            OffsetDateTime start,
            String customerName
    ) {
        CreateBookingCustomerRequest customer = new CreateBookingCustomerRequest();
        customer.setName(customerName);
        customer.setEmail("guest@example.test");
        CreateBookingRequest request = new CreateBookingRequest();
        request.setBranchId(fixture.branch());
        request.setServiceId(fixture.service());
        request.setEmployeeId(employee);
        request.setStart(start);
        request.setCustomer(customer);
        return request;
    }

    private String body(
            Fixture fixture,
            UUID employee,
            OffsetDateTime start,
            String customerName,
            String customerEmail
    ) {
        String employeeField = employee == null ? "" : "\"employeeId\":\"%s\",".formatted(employee);
        return """
                {"branchId":"%s","serviceId":"%s",%s"start":"%s","customer":{"name":"%s","email":"%s"}}
                """.formatted(fixture.branch(), fixture.service(), employeeField, start, customerName, customerEmail);
    }

    private String path(Fixture fixture) {
        return "/api/v1/public/businesses/" + fixture.slug() + "/bookings";
    }

    private String code(String prefix, UUID id) {
        return (prefix + id.toString().replace("-", "").substring(0, 10)).toUpperCase();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countWhere(String table, String column, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private record Fixture(
            String slug,
            UUID business,
            UUID branch,
            UUID service,
            UUID firstEmployee
    ) {
    }

    private record Outcome(boolean success, boolean replayed, UUID bookingId, Throwable error) {
        static Outcome failure(Throwable error) {
            return new Outcome(false, false, null, error);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        BookingCreationService.Result get();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock bookingTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
