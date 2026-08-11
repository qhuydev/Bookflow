package com.bookflow.employees.repository;

import com.bookflow.employees.application.EmployeeCodeAlreadyExistsException;
import com.bookflow.employees.application.EmployeeRequestValidator.Values;
import com.bookflow.employees.domain.Employee;
import com.bookflow.employees.domain.EmployeeStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class JdbcEmployeeRepository implements EmployeeRepository {
    private static final String COLUMNS = """
            e.id, e.tenant_id, e.code, e.full_name, e.phone, e.email, e.bio, e.status, e.created_at, e.updated_at,
            ARRAY(SELECT a.branch_id FROM employee_branch_assignments a
                  JOIN branches b ON b.tenant_id = a.tenant_id AND b.id = a.branch_id
                  WHERE a.tenant_id = e.tenant_id AND a.employee_id = e.id AND b.status = 'ACTIVE'
                  ORDER BY a.branch_id) AS branch_ids
            """;
    private final JdbcTemplate jdbc;

    public JdbcEmployeeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Employee> create(UUID tenantId, UUID userId, Values values) {
        String sql = """
                INSERT INTO employees (id, tenant_id, code, full_name, phone, email, bio)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE EXISTS (
                    SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id = b.id
                    WHERE b.id = ? AND b.status = 'ACTIVE' AND m.user_id = ?
                      AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN')
                )
                RETURNING id, tenant_id, code, full_name, phone, email, bio, status, created_at, updated_at,
                          ARRAY[]::uuid[] AS branch_ids
                """;
        try {
            return jdbc.query(sql, this::map, UUID.randomUUID(), tenantId, values.code(), values.fullName(),
                    values.phone(), values.email(), values.bio(), tenantId, userId).stream().findFirst();
        } catch (DataIntegrityViolationException exception) { throw mapConflict(exception); }
    }

    @Override
    public List<Employee> active(UUID tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM employees e WHERE e.tenant_id = ? AND e.status = 'ACTIVE' ORDER BY e.created_at, e.id", this::map, tenantId);
    }

    @Override
    public Optional<Employee> active(UUID tenantId, UUID employeeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM employees e WHERE e.tenant_id = ? AND e.id = ? AND e.status = 'ACTIVE'", this::map, tenantId, employeeId).stream().findFirst();
    }

    @Override
    public Optional<Employee> update(UUID tenantId, UUID employeeId, UUID userId, Values values) {
        String sql = """
                UPDATE employees e
                SET code = COALESCE(?, e.code), full_name = COALESCE(?, e.full_name), phone = COALESCE(?, e.phone),
                    email = COALESCE(?, e.email), bio = COALESCE(?, e.bio), updated_at = CURRENT_TIMESTAMP
                WHERE e.tenant_id = ? AND e.id = ? AND e.status = 'ACTIVE'
                  AND EXISTS (
                    SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id = b.id
                    WHERE b.id = e.tenant_id AND b.status = 'ACTIVE' AND m.user_id = ?
                      AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN')
                  )
                RETURNING id, tenant_id, code, full_name, phone, email, bio, status, created_at, updated_at,
                          ARRAY(SELECT a.branch_id FROM employee_branch_assignments a
                                JOIN branches b ON b.tenant_id = a.tenant_id AND b.id = a.branch_id
                                WHERE a.tenant_id = e.tenant_id AND a.employee_id = e.id AND b.status = 'ACTIVE'
                                ORDER BY a.branch_id) AS branch_ids
                """;
        try {
            return jdbc.query(sql, this::map, values.code(), values.fullName(), values.phone(), values.email(), values.bio(),
                    tenantId, employeeId, userId).stream().findFirst();
        } catch (DataIntegrityViolationException exception) { throw mapConflict(exception); }
    }

    @Override
    public boolean archive(UUID tenantId, UUID employeeId, UUID userId) {
        return jdbc.update("""
                UPDATE employees e SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
                WHERE e.tenant_id = ? AND e.id = ? AND e.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id = b.id
                              WHERE b.id = e.tenant_id AND b.status = 'ACTIVE' AND m.user_id = ?
                                AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN'))
                """, tenantId, employeeId, userId) == 1;
    }

    @Override
    public boolean exists(UUID tenantId, UUID employeeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM employees WHERE tenant_id = ? AND id = ?)", Boolean.class, tenantId, employeeId));
    }

    @Override
    public boolean assign(UUID tenantId, UUID employeeId, UUID branchId, UUID userId) {
        return jdbc.update("""
                INSERT INTO employee_branch_assignments (tenant_id, employee_id, branch_id)
                SELECT ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM employees e WHERE e.tenant_id = ? AND e.id = ? AND e.status = 'ACTIVE')
                  AND EXISTS (SELECT 1 FROM branches b WHERE b.tenant_id = ? AND b.id = ? AND b.status = 'ACTIVE')
                  AND EXISTS (SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id = b.id
                              WHERE b.id = ? AND b.status = 'ACTIVE' AND m.user_id = ?
                                AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN'))
                ON CONFLICT DO NOTHING
                """, tenantId, employeeId, branchId, tenantId, employeeId, tenantId, branchId, tenantId, userId) == 1;
    }

    @Override
    public boolean activePair(UUID tenantId, UUID employeeId, UUID branchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM employees e JOIN branches b ON b.tenant_id = e.tenant_id
                              WHERE e.tenant_id = ? AND e.id = ? AND e.status = 'ACTIVE'
                                AND b.id = ? AND b.status = 'ACTIVE')
                """, Boolean.class, tenantId, employeeId, branchId));
    }

    @Override
    public void unassign(UUID tenantId, UUID employeeId, UUID branchId, UUID userId) {
        jdbc.update("""
                DELETE FROM employee_branch_assignments a
                WHERE a.tenant_id = ? AND a.employee_id = ? AND a.branch_id = ?
                  AND EXISTS (SELECT 1 FROM businesses b JOIN business_memberships m ON m.tenant_id = b.id
                              WHERE b.id = a.tenant_id AND b.status = 'ACTIVE' AND m.user_id = ?
                                AND m.status = 'ACTIVE' AND m.role IN ('OWNER', 'ADMIN'))
                """, tenantId, employeeId, branchId, userId);
    }

    @Override
    public List<UUID> branchIds(UUID tenantId, UUID employeeId) {
        return jdbc.queryForList("""
                SELECT a.branch_id FROM employee_branch_assignments a
                JOIN branches b ON b.tenant_id = a.tenant_id AND b.id = a.branch_id
                WHERE a.tenant_id = ? AND a.employee_id = ? AND b.status = 'ACTIVE'
                ORDER BY a.branch_id
                """, UUID.class, tenantId, employeeId);
    }

    private Employee map(ResultSet resultSet, int rowNum) throws SQLException {
        java.sql.Array array = resultSet.getArray("branch_ids");
        List<UUID> branchIds = array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(UUID.class::cast).toList();
        return new Employee(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("code"), resultSet.getString("full_name"), resultSet.getString("phone"),
                resultSet.getString("email"), resultSet.getString("bio"), EmployeeStatus.valueOf(resultSet.getString("status")),
                branchIds, resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }

    private RuntimeException mapConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null && sqlException.getMessage().contains("employees_tenant_code_key")) {
                return new EmployeeCodeAlreadyExistsException();
            }
            cause = cause.getCause();
        }
        return exception;
    }
}
