package com.bookflow.bookings.repository;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingCustomer;
import com.bookflow.bookings.domain.BookingItem;
import com.bookflow.bookings.domain.BookingRescheduleHistory;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.BookingStatusHistory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBookingRepository implements BookingRepository {
    private final JdbcTemplate jdbc;

    public JdbcBookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertBooking(Booking booking) {
        jdbc.update("""
                INSERT INTO bookings (
                    id, tenant_id, branch_id, employee_id, customer_user_id,
                    customer_name, customer_email, customer_phone,
                    start_at, end_at, status, currency, total_amount, expires_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                booking.id(), booking.tenantId(), booking.branchId(), booking.employeeId(),
                booking.customer().userId(), booking.customer().name(), booking.customer().email(),
                booking.customer().phone(), Timestamp.from(booking.startAt()), Timestamp.from(booking.endAt()),
                booking.status().name(), booking.currency(), booking.totalAmount(),
                timestamp(booking.expiresAt()), Timestamp.from(booking.createdAt()), Timestamp.from(booking.updatedAt())
        );
    }

    @Override
    public void insertItems(Booking booking) {
        for (BookingItem item : booking.items()) {
            jdbc.update("""
                    INSERT INTO booking_items (
                        id, tenant_id, booking_id, service_id, position,
                        service_name_snapshot, price_snapshot, currency_snapshot,
                        duration_minutes_snapshot, buffer_before_minutes_snapshot,
                        buffer_after_minutes_snapshot, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.id(), item.tenantId(), item.bookingId(), item.serviceId(), item.position(),
                    item.serviceNameSnapshot(), item.priceSnapshot(), item.currencySnapshot(),
                    item.durationMinutesSnapshot(), item.bufferBeforeMinutesSnapshot(),
                    item.bufferAfterMinutesSnapshot(), Timestamp.from(item.createdAt())
            );
        }
    }

    @Override
    public void insertHistory(BookingStatusHistory history) {
        jdbc.update("""
                INSERT INTO booking_status_history (
                    id, tenant_id, booking_id, from_status, to_status,
                    actor_user_id, reason, changed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                history.id(), history.tenantId(), history.bookingId(),
                history.fromStatus() == null ? null : history.fromStatus().name(),
                history.toStatus().name(), history.actorUserId(), history.reason(),
                Timestamp.from(history.changedAt())
        );
    }

    @Override
    public Optional<Booking> findByTenantAndId(UUID tenantId, UUID bookingId) {
        return findBooking("""
                SELECT id, tenant_id, branch_id, employee_id, customer_user_id,
                       customer_name, customer_email, customer_phone,
                       start_at, end_at, status, currency, total_amount, expires_at,
                       created_at, updated_at
                FROM bookings
                WHERE tenant_id = ? AND id = ?
                """, tenantId, bookingId);
    }

    @Override
    public Optional<Booking> findByTenantAndIdForUpdate(UUID tenantId, UUID bookingId) {
        return findBooking("""
                SELECT id, tenant_id, branch_id, employee_id, customer_user_id,
                       customer_name, customer_email, customer_phone,
                       start_at, end_at, status, currency, total_amount, expires_at,
                       created_at, updated_at
                FROM bookings
                WHERE tenant_id = ? AND id = ?
                FOR UPDATE NOWAIT
                """, tenantId, bookingId);
    }

    @Override
    public Optional<Booking> findByCustomerUserAndIdForUpdate(UUID userId, UUID bookingId) {
        return findBooking("""
                SELECT b.id, b.tenant_id, b.branch_id, b.employee_id, b.customer_user_id,
                       b.customer_name, b.customer_email, b.customer_phone,
                       b.start_at, b.end_at, b.status, b.currency, b.total_amount, b.expires_at,
                       b.created_at, b.updated_at
                FROM bookings b
                JOIN businesses business ON business.id = b.tenant_id AND business.status = 'ACTIVE'
                WHERE b.customer_user_id = ? AND b.id = ?
                FOR UPDATE OF b NOWAIT
                """, userId, bookingId);
    }

    @Override
    public List<ExpiryCandidate> findExpiredCandidatesForUpdate(Instant now, int limit) {
        return jdbc.query("""
                SELECT tenant_id, id, status
                FROM bookings
                WHERE status IN ('PENDING_PAYMENT', 'PENDING_CONFIRMATION')
                  AND expires_at <= ?
                ORDER BY expires_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """, (resultSet, rowNumber) -> new ExpiryCandidate(
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("id", UUID.class),
                        BookingStatus.valueOf(resultSet.getString("status"))
                ), Timestamp.from(now), limit);
    }

    @Override
    public boolean updateStatus(
            UUID tenantId,
            UUID bookingId,
            BookingStatus expectedStatus,
            BookingStatus newStatus,
            Instant updatedAt
    ) {
        return jdbc.update("""
                UPDATE bookings
                SET status = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status = ?
                """, newStatus.name(), Timestamp.from(updatedAt), tenantId, bookingId, expectedStatus.name()) == 1;
    }

    @Override
    public boolean updateSchedule(
            UUID tenantId,
            UUID bookingId,
            BookingStatus expectedStatus,
            Instant expectedUpdatedAt,
            UUID employeeId,
            Instant startAt,
            Instant endAt,
            Instant updatedAt
    ) {
        return jdbc.update("""
                UPDATE bookings
                SET employee_id = ?, start_at = ?, end_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status = ? AND updated_at = ?
                """, employeeId, Timestamp.from(startAt), Timestamp.from(endAt), Timestamp.from(updatedAt),
                tenantId, bookingId, expectedStatus.name(), Timestamp.from(expectedUpdatedAt)) == 1;
    }

    @Override
    public void insertRescheduleHistory(BookingRescheduleHistory history) {
        jdbc.update("""
                INSERT INTO booking_reschedule_history (
                    id, tenant_id, booking_id, old_employee_id, new_employee_id,
                    old_start_at, old_end_at, new_start_at, new_end_at,
                    actor_user_id, reason, changed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, history.id(), history.tenantId(), history.bookingId(),
                history.oldEmployeeId(), history.newEmployeeId(),
                Timestamp.from(history.oldStartAt()), Timestamp.from(history.oldEndAt()),
                Timestamp.from(history.newStartAt()), Timestamp.from(history.newEndAt()),
                history.actorUserId(), history.reason(), Timestamp.from(history.changedAt()));
    }

    @Override
    public Optional<RescheduleContext> findRescheduleContext(
            UUID tenantId, UUID branchId, UUID serviceId, UUID employeeId
    ) {
        return jdbc.query("""
                SELECT business.slug, business.time_zone
                FROM businesses business
                JOIN branches branch
                  ON branch.tenant_id = business.id AND branch.id = ? AND branch.status = 'ACTIVE'
                JOIN services service
                  ON service.tenant_id = business.id AND service.id = ? AND service.status = 'ACTIVE'
                JOIN branch_services bs
                  ON bs.tenant_id = business.id AND bs.branch_id = branch.id AND bs.service_id = service.id
                JOIN employees employee
                  ON employee.tenant_id = business.id AND employee.id = ? AND employee.status = 'ACTIVE'
                JOIN employee_branch_assignments eba
                  ON eba.tenant_id = business.id AND eba.employee_id = employee.id AND eba.branch_id = branch.id
                JOIN employee_services es
                  ON es.tenant_id = business.id AND es.employee_id = employee.id AND es.service_id = service.id
                WHERE business.id = ? AND business.status = 'ACTIVE'
                """, (resultSet, rowNumber) -> new RescheduleContext(
                        resultSet.getString("slug"), resultSet.getString("time_zone")
                ), branchId, serviceId, employeeId, tenantId).stream().findFirst();
    }

    private Optional<Booking> findBooking(String sql, UUID first, UUID second) {
        return jdbc.query(sql, (resultSet, rowNumber) -> mapBooking(resultSet), first, second)
                .stream().findFirst();
    }

    private Booking mapBooking(ResultSet resultSet) throws SQLException {
        UUID tenantId = resultSet.getObject("tenant_id", UUID.class);
        UUID bookingId = resultSet.getObject("id", UUID.class);
        List<BookingItem> items = jdbc.query("""
                SELECT id, tenant_id, booking_id, service_id, position,
                       service_name_snapshot, price_snapshot, currency_snapshot,
                       duration_minutes_snapshot, buffer_before_minutes_snapshot,
                       buffer_after_minutes_snapshot, created_at
                FROM booking_items
                WHERE tenant_id = ? AND booking_id = ?
                ORDER BY position, id
                """, this::mapItem, tenantId, bookingId);
        List<BookingStatusHistory> history = jdbc.query("""
                SELECT id, tenant_id, booking_id, from_status, to_status,
                       actor_user_id, reason, changed_at
                FROM booking_status_history
                WHERE tenant_id = ? AND booking_id = ?
                ORDER BY changed_at, id
                """, this::mapHistory, tenantId, bookingId);
        return new Booking(
                bookingId,
                tenantId,
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getObject("employee_id", UUID.class),
                new BookingCustomer(
                        resultSet.getObject("customer_user_id", UUID.class),
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_email"),
                        resultSet.getString("customer_phone")
                ),
                resultSet.getTimestamp("start_at").toInstant(),
                resultSet.getTimestamp("end_at").toInstant(),
                BookingStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("total_amount"),
                instant(resultSet.getTimestamp("expires_at")),
                items,
                history,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private BookingItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BookingItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("booking_id", UUID.class),
                resultSet.getObject("service_id", UUID.class),
                resultSet.getInt("position"),
                resultSet.getString("service_name_snapshot"),
                resultSet.getBigDecimal("price_snapshot"),
                resultSet.getString("currency_snapshot"),
                resultSet.getInt("duration_minutes_snapshot"),
                resultSet.getInt("buffer_before_minutes_snapshot"),
                resultSet.getInt("buffer_after_minutes_snapshot"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private BookingStatusHistory mapHistory(ResultSet resultSet, int rowNumber) throws SQLException {
        String fromStatus = resultSet.getString("from_status");
        return new BookingStatusHistory(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("booking_id", UUID.class),
                fromStatus == null ? null : BookingStatus.valueOf(fromStatus),
                BookingStatus.valueOf(resultSet.getString("to_status")),
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getString("reason"),
                resultSet.getTimestamp("changed_at").toInstant()
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
