package com.bookflow.branches.repository;

import com.bookflow.branches.application.BranchCodeAlreadyExistsException;
import com.bookflow.branches.application.BranchRequestValidator.ValidatedBranchCreate;
import com.bookflow.branches.application.BranchRequestValidator.ValidatedBranchUpdate;
import com.bookflow.branches.domain.Branch;
import com.bookflow.branches.domain.BranchStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcBranchRepository implements BranchRepository {
    private static final String COLUMNS = "id, tenant_id, code, name, address_line1, address_line2, ward, district, city, postal_code, country_code, phone, email, time_zone, status, created_at, updated_at";
    private final JdbcTemplate jdbc;
    public JdbcBranchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Branch> create(UUID tenantId, UUID userId, ValidatedBranchCreate r) {
        try {
            return jdbc.query("""
                    INSERT INTO branches (id, tenant_id, code, name, address_line1, address_line2, ward, district, city, postal_code, country_code, phone, email, time_zone)
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE EXISTS (SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id=b.id
                                  WHERE b.id=? AND b.status='ACTIVE' AND m.user_id=? AND m.status='ACTIVE' AND m.role IN ('OWNER','ADMIN'))
                    RETURNING %s
                    """.formatted(COLUMNS), this::map, UUID.randomUUID(), tenantId, r.code(), r.name(), r.addressLine1(), r.addressLine2(), r.ward(), r.district(), r.city(), r.postalCode(), r.countryCode(), r.phone(), r.email(), r.timeZone(), tenantId, userId).stream().findFirst();
        } catch (DataIntegrityViolationException ex) { throw translateCodeConflict(ex); }
    }
    @Override public List<Branch> findActiveByTenant(UUID tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM branches WHERE tenant_id=? AND status='ACTIVE' ORDER BY created_at ASC, id ASC", this::map, tenantId);
    }
    @Override public Optional<Branch> findActiveByTenantAndId(UUID tenantId, UUID branchId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM branches WHERE tenant_id=? AND id=? AND status='ACTIVE'", this::map, tenantId, branchId).stream().findFirst();
    }
    @Override public Optional<Branch> updateActive(UUID tenantId, UUID branchId, UUID userId, ValidatedBranchUpdate r) {
        try {
            return jdbc.query("""
                    UPDATE branches b SET code=COALESCE(?, b.code), name=COALESCE(?, b.name), address_line1=COALESCE(?, b.address_line1),
                      address_line2=COALESCE(?, b.address_line2), ward=COALESCE(?, b.ward), district=COALESCE(?, b.district), city=COALESCE(?, b.city),
                      postal_code=COALESCE(?, b.postal_code), country_code=COALESCE(?, b.country_code), phone=COALESCE(?, b.phone), email=COALESCE(?, b.email),
                      time_zone=COALESCE(?, b.time_zone), updated_at=CURRENT_TIMESTAMP
                    WHERE b.tenant_id=? AND b.id=? AND b.status='ACTIVE'
                      AND EXISTS (SELECT 1 FROM businesses x JOIN business_memberships m ON m.tenant_id=x.id
                                  WHERE x.id=b.tenant_id AND x.status='ACTIVE' AND m.user_id=? AND m.status='ACTIVE' AND m.role IN ('OWNER','ADMIN'))
                    RETURNING %s
                    """.formatted(COLUMNS), this::map, r.code(), r.name(), r.addressLine1(), r.addressLine2(), r.ward(), r.district(), r.city(), r.postalCode(), r.countryCode(), r.phone(), r.email(), r.timeZone(), tenantId, branchId, userId).stream().findFirst();
        } catch (DataIntegrityViolationException ex) { throw translateCodeConflict(ex); }
    }
    @Override public boolean archiveActive(UUID tenantId, UUID branchId, UUID userId) {
        return jdbc.update("""
                UPDATE branches b SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP
                WHERE b.tenant_id=? AND b.id=? AND b.status='ACTIVE'
                  AND EXISTS (SELECT 1 FROM businesses x JOIN business_memberships m ON m.tenant_id=x.id
                              WHERE x.id=b.tenant_id AND x.status='ACTIVE' AND m.user_id=? AND m.status='ACTIVE' AND m.role IN ('OWNER','ADMIN'))
                """, tenantId, branchId, userId) == 1;
    }
    @Override public boolean existsByTenantAndId(UUID tenantId, UUID branchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM branches WHERE tenant_id=? AND id=?)", Boolean.class, tenantId, branchId));
    }
    private Branch map(ResultSet rs, int row) throws SQLException {
        return new Branch(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("ward"), rs.getString("district"), rs.getString("city"), rs.getString("postal_code"), rs.getString("country_code"), rs.getString("phone"), rs.getString("email"), rs.getString("time_zone"), BranchStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private RuntimeException translateCodeConflict(DataIntegrityViolationException ex) {
        Throwable cause=ex; while(cause!=null) { if(cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState()) && sql.getMessage()!=null && sql.getMessage().contains("branches_tenant_code_key")) return new BranchCodeAlreadyExistsException(); cause=cause.getCause(); } return ex;
    }
}
