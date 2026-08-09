package com.bookflow.businesses.repository;

import com.bookflow.businesses.domain.Business;
import com.bookflow.businesses.domain.BusinessMembershipView;
import com.bookflow.businesses.domain.BusinessStatus;
import com.bookflow.businesses.domain.BusinessType;
import com.bookflow.businesses.domain.MembershipRole;
import com.bookflow.businesses.domain.MembershipStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBusinessQueryRepository implements BusinessQueryRepository {
    private static final String ACTIVE_BUSINESS_SELECT = """
            SELECT b.id, b.name, b.slug, b.business_type, b.time_zone, b.status,
                   b.created_at, b.updated_at, m.role AS membership_role, m.status AS membership_status
            FROM business_memberships m
            JOIN businesses b ON b.id = m.tenant_id
            WHERE m.user_id = ?
              AND m.status = 'ACTIVE'
              AND b.status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbc;

    public JdbcBusinessQueryRepository(JdbcTemplate jdbc) {
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
    public List<BusinessMembershipView> findActiveBusinessesForUser(UUID userId) {
        return jdbc.query(ACTIVE_BUSINESS_SELECT + " ORDER BY b.created_at ASC, b.id ASC", this::mapView, userId);
    }

    @Override
    public Optional<BusinessMembershipView> findActiveBusinessForUser(UUID userId, UUID businessId) {
        return jdbc.query(ACTIVE_BUSINESS_SELECT + " AND b.id = ?", this::mapView, userId, businessId)
                .stream()
                .findFirst();
    }

    private BusinessMembershipView mapView(ResultSet resultSet, int rowNumber) throws SQLException {
        Business business = new Business(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("slug"),
                BusinessType.valueOf(resultSet.getString("business_type")),
                resultSet.getString("time_zone"),
                BusinessStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
        return new BusinessMembershipView(
                business,
                MembershipRole.valueOf(resultSet.getString("membership_role")),
                MembershipStatus.valueOf(resultSet.getString("membership_status"))
        );
    }
}
