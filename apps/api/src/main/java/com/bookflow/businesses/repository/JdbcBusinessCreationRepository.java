package com.bookflow.businesses.repository;

import com.bookflow.businesses.application.BusinessSlugAlreadyExistsException;
import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBusinessCreationRepository implements BusinessCreationRepository {

    private static final String BUSINESS_SLUG_UNIQUE_CONSTRAINT = "businesses_slug_key";

    private final JdbcTemplate jdbc;

    public JdbcBusinessCreationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasActiveUser(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE id=? AND status='ACTIVE')",
                Boolean.class,
                userId
        ));
    }

    @Override
    public Business insertBusiness(
            UUID id,
            String name,
            String slug,
            BusinessType businessType,
            String timeZone,
            BusinessStatus status
    ) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO businesses (id, name, slug, business_type, time_zone, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, name, slug, business_type, time_zone, status, created_at, updated_at
                    """, this::mapBusiness, id, name, slug, businessType.name(), timeZone, status.name());
        } catch (DataIntegrityViolationException exception) {
            if (isBusinessSlugUniqueViolation(exception)) {
                throw new BusinessSlugAlreadyExistsException();
            }
            throw exception;
        }
    }

    @Override
    public void insertInitialOwnerMembership(UUID tenantId, UUID userId) {
        jdbc.update("""
                INSERT INTO business_memberships (id, tenant_id, user_id, role, status)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, userId, MembershipRole.OWNER.name(), MembershipStatus.ACTIVE.name());
    }

    private Business mapBusiness(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Business(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("slug"),
                BusinessType.valueOf(resultSet.getString("business_type")),
                resultSet.getString("time_zone"),
                BusinessStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private boolean isBusinessSlugUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(BUSINESS_SLUG_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
