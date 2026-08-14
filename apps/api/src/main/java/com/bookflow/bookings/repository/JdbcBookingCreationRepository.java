package com.bookflow.bookings.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBookingCreationRepository implements BookingCreationRepository {
    private final JdbcTemplate jdbc;

    public JdbcBookingCreationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CreationContext> findCreationContext(
            String slug,
            UUID branchId,
            UUID serviceId,
            UUID employeeId
    ) {
        return jdbc.query("""
                SELECT b.id AS tenant_id, br.id AS branch_id, s.id AS service_id,
                       e.id AS employee_id, br.time_zone, s.name AS service_name,
                       s.price, s.currency, s.duration_minutes,
                       s.buffer_before_minutes, s.buffer_after_minutes
                FROM businesses b
                JOIN branches br
                  ON br.tenant_id = b.id AND br.id = ? AND br.status = 'ACTIVE'
                JOIN services s
                  ON s.tenant_id = b.id AND s.id = ? AND s.status = 'ACTIVE'
                JOIN branch_services bs
                  ON bs.tenant_id = b.id AND bs.branch_id = br.id AND bs.service_id = s.id
                JOIN employees e
                  ON e.tenant_id = b.id AND e.id = ? AND e.status = 'ACTIVE'
                JOIN employee_branch_assignments eba
                  ON eba.tenant_id = b.id AND eba.employee_id = e.id AND eba.branch_id = br.id
                JOIN employee_services es
                  ON es.tenant_id = b.id AND es.employee_id = e.id AND es.service_id = s.id
                WHERE b.slug = ? AND b.status = 'ACTIVE'
                """, (resultSet, row) -> new CreationContext(
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("branch_id", UUID.class),
                        resultSet.getObject("service_id", UUID.class),
                        resultSet.getObject("employee_id", UUID.class),
                        ZoneId.of(resultSet.getString("time_zone")),
                        resultSet.getString("service_name"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getString("currency"),
                        resultSet.getInt("duration_minutes"),
                        resultSet.getInt("buffer_before_minutes"),
                        resultSet.getInt("buffer_after_minutes")
                ), branchId, serviceId, employeeId, slug).stream().findFirst();
    }

    @Override
    public boolean claimIdempotencyKey(
            UUID id,
            UUID tenantId,
            String key,
            String fingerprint,
            Instant createdAt
    ) {
        return jdbc.update("""
                INSERT INTO booking_idempotency_keys (
                    id, tenant_id, idempotency_key, request_fingerprint, created_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """, id, tenantId, key, fingerprint, Timestamp.from(createdAt)) == 1;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyKeyForUpdate(UUID tenantId, String key) {
        return jdbc.query("""
                SELECT request_fingerprint, booking_id
                FROM booking_idempotency_keys
                WHERE tenant_id = ? AND idempotency_key = ?
                FOR UPDATE
                """, (resultSet, row) -> new IdempotencyRecord(
                        resultSet.getString("request_fingerprint"),
                        resultSet.getObject("booking_id", UUID.class)
                ), tenantId, key).stream().findFirst();
    }

    @Override
    public void completeIdempotencyKey(
            UUID tenantId,
            String key,
            UUID bookingId,
            Instant completedAt
    ) {
        int updated = jdbc.update("""
                UPDATE booking_idempotency_keys
                SET booking_id = ?, completed_at = ?
                WHERE tenant_id = ? AND idempotency_key = ?
                  AND booking_id IS NULL AND completed_at IS NULL
                """, bookingId, Timestamp.from(completedAt), tenantId, key);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record could not be completed.");
        }
    }
}
