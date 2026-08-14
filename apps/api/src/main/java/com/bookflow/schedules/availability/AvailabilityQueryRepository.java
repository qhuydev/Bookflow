package com.bookflow.schedules.availability;

import com.bookflow.schedules.domain.ScheduleBreak;
import com.bookflow.schedules.domain.ScheduleException;
import com.bookflow.schedules.domain.ScheduleExceptionType;
import com.bookflow.schedules.domain.WorkingScheduleRule;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class AvailabilityQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AvailabilityQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<ResourceContext> findResourceContext(String slug, UUID branchId, UUID serviceId) {
        String sql = """
                SELECT b.id, br.id, s.id, br.time_zone, b.max_booking_advance_days,
                       s.duration_minutes, s.buffer_before_minutes, s.buffer_after_minutes
                FROM businesses b
                JOIN branches br ON br.tenant_id = b.id AND br.id = :branchId AND br.status = 'ACTIVE'
                JOIN services s ON s.tenant_id = b.id AND s.id = :serviceId AND s.status = 'ACTIVE'
                JOIN branch_services bs ON bs.tenant_id = b.id AND bs.branch_id = br.id AND bs.service_id = s.id
                WHERE b.slug = :slug AND b.status = 'ACTIVE'
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("slug", slug).addValue("branchId", branchId).addValue("serviceId", serviceId),
                (rs, row) -> new ResourceContext(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        ZoneId.of(rs.getString(4)), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)))
                .stream().findFirst();
    }

    public List<UUID> findEligibleEmployees(ResourceContext context, UUID employeeId) {
        String sql = """
                SELECT DISTINCT e.id
                FROM employees e
                JOIN employee_branch_assignments eba
                  ON eba.tenant_id = e.tenant_id AND eba.employee_id = e.id AND eba.branch_id = :branchId
                JOIN employee_services es
                  ON es.tenant_id = e.tenant_id AND es.employee_id = e.id AND es.service_id = :serviceId
                WHERE e.tenant_id = :tenantId AND e.status = 'ACTIVE'
                """ + (employeeId == null ? "" : " AND e.id = :employeeId\n") + """
                ORDER BY e.id
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", context.businessId()).addValue("branchId", context.branchId())
                .addValue("serviceId", context.serviceId());
        if (employeeId != null) parameters.addValue("employeeId", employeeId);
        return jdbc.queryForList(sql, parameters, UUID.class);
    }

    public List<WorkingScheduleRule> findWorkingRules(
            ResourceContext context, List<UUID> employeeIds, LocalDate date
    ) {
        if (employeeIds.isEmpty()) return List.of();
        String sql = """
                SELECT id, tenant_id, branch_id, employee_id, weekday, start_local_time, end_local_time,
                       effective_from, effective_to, created_at, updated_at
                FROM working_schedule_rules
                WHERE tenant_id = :tenantId AND branch_id = :branchId
                  AND employee_id IN (:employeeIds) AND weekday = :weekday
                  AND effective_from <= :date AND (effective_to IS NULL OR effective_to >= :date)
                ORDER BY employee_id, start_local_time, id
                """;
        return jdbc.query(sql, scheduleParameters(context, employeeIds, date)
                .addValue("weekday", date.getDayOfWeek().name()), this::mapRule);
    }

    public List<ScheduleBreak> findBreaks(UUID businessId, List<UUID> ruleIds) {
        if (ruleIds.isEmpty()) return List.of();
        String sql = """
                SELECT id, tenant_id, schedule_rule_id, start_local_time, end_local_time, created_at, updated_at
                FROM schedule_breaks
                WHERE tenant_id = :tenantId AND schedule_rule_id IN (:ruleIds)
                ORDER BY schedule_rule_id, start_local_time, id
                """;
        return jdbc.query(sql, new MapSqlParameterSource().addValue("tenantId", businessId)
                .addValue("ruleIds", ruleIds), this::mapBreak);
    }

    public List<ScheduleException> findExceptions(
            ResourceContext context, List<UUID> employeeIds, LocalDate date
    ) {
        if (employeeIds.isEmpty()) return List.of();
        String sql = """
                SELECT id, tenant_id, branch_id, employee_id, exception_date, type,
                       start_local_time, end_local_time, note, created_at, updated_at
                FROM schedule_exceptions
                WHERE tenant_id = :tenantId AND branch_id = :branchId
                  AND employee_id IN (:employeeIds) AND exception_date = :date
                ORDER BY employee_id, type, start_local_time NULLS FIRST, id
                """;
        return jdbc.query(sql, scheduleParameters(context, employeeIds, date), this::mapException);
    }

    private MapSqlParameterSource scheduleParameters(ResourceContext context, List<UUID> employeeIds, LocalDate date) {
        return new MapSqlParameterSource().addValue("tenantId", context.businessId())
                .addValue("branchId", context.branchId()).addValue("employeeIds", employeeIds).addValue("date", date);
    }

    private WorkingScheduleRule mapRule(ResultSet rs, int row) throws SQLException {
        return new WorkingScheduleRule(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), DayOfWeek.valueOf(rs.getString(5)),
                rs.getTime(6).toLocalTime(), rs.getTime(7).toLocalTime(), rs.getObject(8, LocalDate.class),
                rs.getObject(9, LocalDate.class), rs.getTimestamp(10).toInstant(), rs.getTimestamp(11).toInstant());
    }

    private ScheduleBreak mapBreak(ResultSet rs, int row) throws SQLException {
        return new ScheduleBreak(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getTime(4).toLocalTime(), rs.getTime(5).toLocalTime(),
                rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant());
    }

    private ScheduleException mapException(ResultSet rs, int row) throws SQLException {
        return new ScheduleException(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, LocalDate.class),
                ScheduleExceptionType.valueOf(rs.getString(6)),
                rs.getTime(7) == null ? null : rs.getTime(7).toLocalTime(),
                rs.getTime(8) == null ? null : rs.getTime(8).toLocalTime(), rs.getString(9),
                rs.getTimestamp(10).toInstant(), rs.getTimestamp(11).toInstant());
    }

    public record ResourceContext(
            UUID businessId, UUID branchId, UUID serviceId, ZoneId zoneId, int maxBookingAdvanceDays,
            int durationMinutes, int bufferBeforeMinutes, int bufferAfterMinutes
    ) { }
}
