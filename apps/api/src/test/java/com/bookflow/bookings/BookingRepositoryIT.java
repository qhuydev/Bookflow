package com.bookflow.bookings;

import com.bookflow.BookFlowApplication;
import com.bookflow.bookings.application.BookingPersistenceService;
import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingCustomer;
import com.bookflow.bookings.domain.BookingItem;
import com.bookflow.bookings.domain.BookingItemSnapshot;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.support.PostgresTestcontainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = BookFlowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class BookingRepositoryIT {
    private static final Instant CREATED_AT = Instant.parse("2026-08-14T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Autowired
    private BookingPersistenceService bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("""
                TRUNCATE TABLE booking_status_history, booking_items, bookings,
                    schedule_breaks, schedule_exceptions, working_schedule_rules,
                    employee_services, branch_services, services,
                    employee_branch_assignments, employees, branches,
                    business_memberships, businesses, refresh_tokens, auth_sessions, users CASCADE
                """);
    }

    @Test
    void persistsLoadsSnapshotsHistoryAndInstantValuesWithoutReadingCurrentService() {
        Fixture fixture = fixture("main");
        UUID secondService = service(fixture.tenantId(), "Gội đầu", "50000.00", 30);
        Booking original = booking(
                fixture,
                List.of(
                        snapshot(fixture.serviceId(), "Cắt tóc", "100000.00", 60),
                        snapshot(secondService, "Gội đầu", "50000.00", 30)
                )
        );

        bookings.create(original);
        jdbc.update("""
                UPDATE services SET name = 'Giá và tên mới', price = 999999, duration_minutes = 15
                WHERE tenant_id = ? AND id = ?
                """, fixture.tenantId(), fixture.serviceId());

        Booking loaded = bookings.find(fixture.tenantId(), original.id());
        assertThat(loaded.startAt()).isEqualTo(original.startAt());
        assertThat(loaded.endAt()).isEqualTo(original.endAt());
        assertThat(loaded.createdAt()).isEqualTo(CREATED_AT);
        assertThat(loaded.totalAmount()).isEqualByComparingTo("150000.00");
        assertThat(loaded.items()).extracting(BookingItem::serviceNameSnapshot)
                .containsExactly("Cắt tóc", "Gội đầu");
        assertThat(loaded.items().getFirst().priceSnapshot()).isEqualByComparingTo("100000.00");
        assertThat(loaded.items().getFirst().durationMinutesSnapshot()).isEqualTo(60);
        assertThat(loaded.statusHistory()).singleElement().satisfies(history -> {
            assertThat(history.fromStatus()).isNull();
            assertThat(history.toStatus()).isEqualTo(BookingStatus.PENDING_CONFIRMATION);
        });

        Booking transitioned = bookings.transition(
                fixture.tenantId(), original.id(), BookingStatus.CONFIRMED,
                fixture.userId(), "Confirmed by business"
        );
        Booking reloaded = bookings.find(fixture.tenantId(), original.id());
        assertThat(transitioned.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.statusHistory()).hasSize(2);
        assertThat(reloaded.statusHistory().getLast().fromStatus())
                .isEqualTo(BookingStatus.PENDING_CONFIRMATION);
        assertThat(reloaded.statusHistory().getLast().toStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void transactionRollsBackBookingWhenTenantScopedServiceItemIsInvalid() {
        Fixture tenantA = fixture("tenant-a");
        Fixture tenantB = fixture("tenant-b");
        Booking invalid = booking(
                tenantA,
                List.of(snapshot(tenantB.serviceId(), "Foreign service", "1000.00", 30))
        );

        assertThatThrownBy(() -> bookings.create(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("SELECT COUNT(*) FROM bookings WHERE id = ?", invalid.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM booking_items WHERE booking_id = ?", invalid.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM booking_status_history WHERE booking_id = ?", invalid.id())).isZero();
    }

    @Test
    void statusUpdateRollsBackWhenHistoryCannotBeInserted() {
        Fixture fixture = fixture("transition-rollback");
        Booking booking = booking(
                fixture,
                List.of(snapshot(fixture.serviceId(), "Service", "1000.00", 30))
        );
        bookings.create(booking);

        assertThatThrownBy(() -> bookings.transition(
                fixture.tenantId(), booking.id(), BookingStatus.CONFIRMED,
                UUID.randomUUID(), "Actor does not exist"
        )).isInstanceOf(DataIntegrityViolationException.class);

        Booking reloaded = bookings.find(fixture.tenantId(), booking.id());
        assertThat(reloaded.status()).isEqualTo(BookingStatus.PENDING_CONFIRMATION);
        assertThat(reloaded.statusHistory()).hasSize(1);
    }

    @Test
    void databaseRejectsCrossTenantBranchAndEmployeeRelationships() {
        Fixture tenantA = fixture("scope-a");
        Fixture tenantB = fixture("scope-b");

        Booking wrongBranch = booking(tenantA, tenantB.branchId(), tenantA.employeeId(), tenantA.serviceId());
        Booking wrongEmployee = booking(tenantA, tenantA.branchId(), tenantB.employeeId(), tenantA.serviceId());

        assertThatThrownBy(() -> bookings.create(wrongBranch))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> bookings.create(wrongEmployee))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("SELECT COUNT(*) FROM bookings WHERE tenant_id = ?", tenantA.tenantId())).isZero();
    }

    @Test
    void exclusionConstraintRejectsOverlapAndAllowsTouchingHalfOpenBoundary() {
        Fixture fixture = fixture("database-overlap");
        Booking first = bookingAt(fixture, "2026-08-20T09:00:00Z", "2026-08-20T10:00:00Z");
        Booking touching = bookingAt(fixture, "2026-08-20T10:00:00Z", "2026-08-20T11:00:00Z");
        Booking overlapping = bookingAt(fixture, "2026-08-20T09:30:00Z", "2026-08-20T10:30:00Z");

        bookings.create(first);
        bookings.create(touching);
        assertThatThrownBy(() -> bookings.create(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("SELECT COUNT(*) FROM bookings WHERE tenant_id = ?", fixture.tenantId()))
                .isEqualTo(2);
    }

    @Test
    void migrationProvidesStatusChecksTenantFirstIndexesAndExclusionConstraint() {
        assertThat(indexes("bookings")).contains(
                "bookings_pkey",
                "bookings_tenant_id_id_key",
                "idx_bookings_tenant_branch_start",
                "idx_bookings_tenant_employee_time_status",
                "idx_bookings_tenant_status_start",
                "idx_bookings_expiry_candidates"
        );
        assertThat(indexes("booking_items")).contains("idx_booking_items_tenant_booking");
        assertThat(indexes("booking_status_history")).contains("idx_booking_history_tenant_booking_changed");
        assertThat(countBySql("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conrelid = 'bookings'::regclass AND contype = 'x'
                """)).isEqualTo(1);
        String constraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'bookings'::regclass
                  AND conname = 'no_overlapping_active_employee_bookings'
                """, String.class);
        assertThat(constraint).contains("tstzrange(start_at, end_at, '[)'::text)");
        java.util.Arrays.stream(BookingStatus.values()).forEach(status -> {
            if (status.occupiesSlot()) {
                assertThat(constraint).contains(status.name());
            } else {
                assertThat(constraint).doesNotContain(status.name());
            }
        });
        assertThat(indexes("booking_idempotency_keys")).contains(
                "booking_idempotency_keys_pkey",
                "booking_idempotency_keys_tenant_key",
                "idx_booking_idempotency_tenant_booking"
        );
        assertThat(indexes("booking_reschedule_history")).contains(
                "idx_booking_reschedule_history_tenant_booking_changed"
        );
        assertThat(columnType("bookings", "start_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("bookings", "end_at")).isEqualTo("timestamp with time zone");
    }

    private Booking booking(Fixture fixture, List<BookingItemSnapshot> snapshots) {
        return booking(fixture, fixture.branchId(), fixture.employeeId(), snapshots);
    }

    private Booking bookingAt(Fixture fixture, String start, String end) {
        return Booking.create(
                UUID.randomUUID(), fixture.tenantId(), fixture.branchId(), fixture.employeeId(),
                new BookingCustomer(fixture.userId(), "Customer", "customer@example.test", null),
                Instant.parse(start), Instant.parse(end),
                List.of(snapshot(fixture.serviceId(), "Service", "1000.00", 60)),
                Duration.ofMinutes(10), CLOCK
        );
    }

    private Booking booking(Fixture fixture, UUID branchId, UUID employeeId, UUID serviceId) {
        return booking(fixture, branchId, employeeId,
                List.of(snapshot(serviceId, "Service snapshot", "1000.00", 30)));
    }

    private Booking booking(
            Fixture fixture,
            UUID branchId,
            UUID employeeId,
            List<BookingItemSnapshot> snapshots
    ) {
        Instant start = Instant.parse("2026-08-20T02:00:00Z");
        return Booking.create(
                UUID.randomUUID(), fixture.tenantId(), branchId, employeeId,
                new BookingCustomer(fixture.userId(), "Customer", "customer@example.test", null),
                start, start.plus(Duration.ofMinutes(90)), snapshots,
                Duration.ofMinutes(10), CLOCK
        );
    }

    private BookingItemSnapshot snapshot(UUID serviceId, String name, String price, int duration) {
        return new BookingItemSnapshot(
                serviceId, name, new BigDecimal(price), "VND", duration, 5, 10
        );
    }

    private Fixture fixture(String suffix) {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, normalized_email, password_hash) VALUES (?, ?, ?)",
                user, suffix + "-" + user + "@example.test", "$argon2id$booking-test-only");
        jdbc.update("""
                INSERT INTO businesses (id, name, slug, business_type, time_zone, status)
                VALUES (?, ?, ?, 'SALON', 'Asia/Ho_Chi_Minh', 'ACTIVE')
                """, tenant, "Business " + suffix, suffix + "-" + tenant);
        jdbc.update("""
                INSERT INTO branches (
                    id, tenant_id, code, name, address_line1, city, country_code, time_zone, status
                ) VALUES (?, ?, ?, ?, '1 Main', 'HCM', 'VN', 'Asia/Ho_Chi_Minh', 'ACTIVE')
                """, branch, tenant, "B" + tenant.toString().substring(0, 6).toUpperCase(), "Branch " + suffix);
        jdbc.update("""
                INSERT INTO employees (id, tenant_id, code, full_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, employee, tenant, "E" + tenant.toString().substring(0, 6).toUpperCase(), "Employee " + suffix);
        UUID service = service(tenant, "Service " + suffix, "100000.00", 60);
        return new Fixture(tenant, user, branch, employee, service);
    }

    private UUID service(UUID tenant, String name, String price, int duration) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services (
                    id, tenant_id, name, price, currency, duration_minutes,
                    buffer_before_minutes, buffer_after_minutes, status
                ) VALUES (?, ?, ?, ?, 'VND', ?, 5, 10, 'ACTIVE')
                """, id, tenant, name, new BigDecimal(price), duration);
        return id;
    }

    private int count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private int countBySql(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = ?
                """, String.class, table);
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private record Fixture(
            UUID tenantId,
            UUID userId,
            UUID branchId,
            UUID employeeId,
            UUID serviceId
    ) {
    }
}
