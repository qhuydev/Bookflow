package com.bookflow.businesses.repository;

import com.bookflow.businesses.application.BusinessSlugAlreadyExistsException;
import com.bookflow.businesses.application.BusinessUpdateRequestValidator.ValidatedBusinessUpdate;
import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.CancellationPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBusinessConfigurationRepository implements BusinessConfigurationRepository {
    private final JdbcTemplate jdbc;
    public JdbcBusinessConfigurationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Business> updateActiveBusiness(UUID businessId, UUID userId, ValidatedBusinessUpdate update) {
        try {
            return jdbc.query("""
                    UPDATE businesses b SET
                        name = COALESCE(?, b.name), slug = COALESCE(?, b.slug),
                        business_type = COALESCE(?, b.business_type), time_zone = COALESCE(?, b.time_zone),
                        currency_code = COALESCE(?, b.currency_code), cancellation_policy = COALESCE(?, b.cancellation_policy),
                        max_booking_advance_days = COALESCE(?, b.max_booking_advance_days), updated_at = CURRENT_TIMESTAMP
                    WHERE b.id = ? AND b.status = 'ACTIVE'
                      AND EXISTS (SELECT 1 FROM business_memberships m WHERE m.tenant_id = b.id AND m.user_id = ?
                                  AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN'))
                    RETURNING b.id, b.name, b.slug, b.business_type, b.time_zone, b.currency_code,
                              b.cancellation_policy, b.max_booking_advance_days, b.status, b.created_at, b.updated_at
                    """, this::mapBusiness, update.name(), update.slug(),
                    update.type() == null ? null : update.type().name(), update.timeZone(), update.currencyCode(),
                    update.cancellationPolicy() == null ? null : update.cancellationPolicy().name(), update.maxBookingAdvanceDays(), businessId, userId)
                    .stream().findFirst();
        } catch (DataIntegrityViolationException ex) {
            if (isSlugViolation(ex)) throw new BusinessSlugAlreadyExistsException();
            throw ex;
        }
    }
    private Business mapBusiness(ResultSet rs, int row) throws SQLException {
        return new Business(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                BusinessType.valueOf(rs.getString("business_type")), rs.getString("time_zone"), rs.getString("currency_code"),
                CancellationPolicy.valueOf(rs.getString("cancellation_policy")), rs.getInt("max_booking_advance_days"),
                BusinessStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private boolean isSlugViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("businesses_slug_key")) return true;
            current = current.getCause();
        }
        return false;
    }
}
