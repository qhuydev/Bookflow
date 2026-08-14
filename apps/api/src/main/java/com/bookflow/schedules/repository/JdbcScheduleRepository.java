package com.bookflow.schedules.repository;

import com.bookflow.schedules.application.ScheduleRequestValidator.*;
import com.bookflow.schedules.domain.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.*;

@Repository
@Profile("!test")
public class JdbcScheduleRepository implements ScheduleRepository {
    private static final String RULE_COLUMNS = "id, tenant_id, branch_id, employee_id, weekday, start_local_time, end_local_time, effective_from, effective_to, created_at, updated_at";
    private static final String BREAK_COLUMNS = "id, tenant_id, schedule_rule_id, start_local_time, end_local_time, created_at, updated_at";
    private static final String EXCEPTION_COLUMNS = "id, tenant_id, branch_id, employee_id, exception_date, type, start_local_time, end_local_time, note, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public JdbcScheduleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockRuleKey(UUID tenant, UUID employee, UUID branch, DayOfWeek weekday) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> null,
                tenant + ":" + employee + ":" + branch + ":" + weekday);
    }
    @Override public boolean ruleOverlaps(UUID tenant, UUID employee, RuleValues v, UUID excluded) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM working_schedule_rules
                 WHERE tenant_id=? AND employee_id=? AND branch_id=? AND weekday=?
                   AND (?::uuid IS NULL OR id<>?::uuid)
                   AND start_local_time < ? AND end_local_time > ?
                   AND effective_from <= COALESCE(?::date, 'infinity'::date)
                   AND COALESCE(effective_to, 'infinity'::date) >= ?)
                """, Boolean.class, tenant, employee, v.branchId(), v.weekday().name(), excluded, excluded,
                v.endLocalTime(), v.startLocalTime(), v.effectiveTo(), v.effectiveFrom()));
    }
    @Override public WorkingScheduleRule createRule(UUID tenant, UUID employee, RuleValues v) {
        return jdbc.queryForObject("INSERT INTO working_schedule_rules (id,tenant_id,branch_id,employee_id,weekday,start_local_time,end_local_time,effective_from,effective_to) VALUES (?,?,?,?,?,?,?,?,?) RETURNING " + RULE_COLUMNS,
                this::rule, UUID.randomUUID(), tenant, v.branchId(), employee, v.weekday().name(), v.startLocalTime(), v.endLocalTime(), v.effectiveFrom(), v.effectiveTo());
    }
    @Override public List<WorkingScheduleRule> listRules(UUID tenant, UUID employee) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM working_schedule_rules WHERE tenant_id=? AND employee_id=? ORDER BY weekday,effective_from,start_local_time,id", this::rule, tenant, employee);
    }
    @Override public Optional<WorkingScheduleRule> findRule(UUID tenant, UUID employee, UUID id) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM working_schedule_rules WHERE tenant_id=? AND employee_id=? AND id=?", this::rule, tenant, employee, id).stream().findFirst();
    }
    @Override public Optional<WorkingScheduleRule> updateRule(UUID tenant, UUID employee, UUID id, RuleValues v) {
        return jdbc.query("UPDATE working_schedule_rules SET branch_id=?,weekday=?,start_local_time=?,end_local_time=?,effective_from=?,effective_to=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND employee_id=? AND id=? RETURNING " + RULE_COLUMNS,
                this::rule, v.branchId(), v.weekday().name(), v.startLocalTime(), v.endLocalTime(), v.effectiveFrom(), v.effectiveTo(), tenant, employee, id).stream().findFirst();
    }
    @Override public boolean deleteRule(UUID tenant, UUID employee, UUID id) {
        return jdbc.update("DELETE FROM working_schedule_rules WHERE tenant_id=? AND employee_id=? AND id=?", tenant, employee, id) == 1;
    }

    @Override public boolean breakOverlaps(UUID tenant, UUID rule, BreakValues v, UUID excluded) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM schedule_breaks WHERE tenant_id=? AND schedule_rule_id=?
                  AND (?::uuid IS NULL OR id<>?::uuid) AND start_local_time < ? AND end_local_time > ?)
                """, Boolean.class, tenant, rule, excluded, excluded, v.endLocalTime(), v.startLocalTime()));
    }
    @Override public ScheduleBreak createBreak(UUID tenant, UUID rule, BreakValues v) {
        return jdbc.queryForObject("INSERT INTO schedule_breaks (id,tenant_id,schedule_rule_id,start_local_time,end_local_time) VALUES (?,?,?,?,?) RETURNING " + BREAK_COLUMNS,
                this::scheduleBreak, UUID.randomUUID(), tenant, rule, v.startLocalTime(), v.endLocalTime());
    }
    @Override public List<ScheduleBreak> listBreaks(UUID tenant, UUID rule) {
        return jdbc.query("SELECT " + BREAK_COLUMNS + " FROM schedule_breaks WHERE tenant_id=? AND schedule_rule_id=? ORDER BY start_local_time,id", this::scheduleBreak, tenant, rule);
    }
    @Override public Optional<ScheduleBreak> findBreak(UUID tenant, UUID rule, UUID id) {
        return jdbc.query("SELECT " + BREAK_COLUMNS + " FROM schedule_breaks WHERE tenant_id=? AND schedule_rule_id=? AND id=?", this::scheduleBreak, tenant, rule, id).stream().findFirst();
    }
    @Override public Optional<ScheduleBreak> updateBreak(UUID tenant, UUID rule, UUID id, BreakValues v) {
        return jdbc.query("UPDATE schedule_breaks SET start_local_time=?,end_local_time=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND schedule_rule_id=? AND id=? RETURNING " + BREAK_COLUMNS,
                this::scheduleBreak, v.startLocalTime(), v.endLocalTime(), tenant, rule, id).stream().findFirst();
    }
    @Override public boolean deleteBreak(UUID tenant, UUID rule, UUID id) {
        return jdbc.update("DELETE FROM schedule_breaks WHERE tenant_id=? AND schedule_rule_id=? AND id=?", tenant, rule, id) == 1;
    }

    @Override public ScheduleException createException(UUID tenant, UUID employee, ExceptionValues v) {
        return jdbc.queryForObject("INSERT INTO schedule_exceptions (id,tenant_id,branch_id,employee_id,exception_date,type,start_local_time,end_local_time,note) VALUES (?,?,?,?,?,?,?,?,?) RETURNING " + EXCEPTION_COLUMNS,
                this::exception, UUID.randomUUID(), tenant, v.branchId(), employee, v.date(), v.type().name(), v.startLocalTime(), v.endLocalTime(), v.note());
    }
    @Override public List<ScheduleException> listExceptions(UUID tenant, UUID employee) {
        return jdbc.query("SELECT " + EXCEPTION_COLUMNS + " FROM schedule_exceptions WHERE tenant_id=? AND employee_id=? ORDER BY exception_date,start_local_time NULLS FIRST,id", this::exception, tenant, employee);
    }
    @Override public Optional<ScheduleException> findException(UUID tenant, UUID employee, UUID id) {
        return jdbc.query("SELECT " + EXCEPTION_COLUMNS + " FROM schedule_exceptions WHERE tenant_id=? AND employee_id=? AND id=?", this::exception, tenant, employee, id).stream().findFirst();
    }
    @Override public Optional<ScheduleException> updateException(UUID tenant, UUID employee, UUID id, ExceptionValues v) {
        return jdbc.query("UPDATE schedule_exceptions SET branch_id=?,exception_date=?,type=?,start_local_time=?,end_local_time=?,note=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND employee_id=? AND id=? RETURNING " + EXCEPTION_COLUMNS,
                this::exception, v.branchId(), v.date(), v.type().name(), v.startLocalTime(), v.endLocalTime(), v.note(), tenant, employee, id).stream().findFirst();
    }
    @Override public boolean deleteException(UUID tenant, UUID employee, UUID id) {
        return jdbc.update("DELETE FROM schedule_exceptions WHERE tenant_id=? AND employee_id=? AND id=?", tenant, employee, id) == 1;
    }

    private WorkingScheduleRule rule(ResultSet r, int n) throws SQLException {
        java.sql.Date to = r.getDate("effective_to");
        return new WorkingScheduleRule(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("branch_id",UUID.class),r.getObject("employee_id",UUID.class),
                DayOfWeek.valueOf(r.getString("weekday")),r.getTime("start_local_time").toLocalTime(),r.getTime("end_local_time").toLocalTime(),
                r.getDate("effective_from").toLocalDate(),to==null?null:to.toLocalDate(),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    private ScheduleBreak scheduleBreak(ResultSet r, int n) throws SQLException {
        return new ScheduleBreak(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("schedule_rule_id",UUID.class),
                r.getTime("start_local_time").toLocalTime(),r.getTime("end_local_time").toLocalTime(),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    private ScheduleException exception(ResultSet r, int n) throws SQLException {
        Time start=r.getTime("start_local_time"),end=r.getTime("end_local_time");
        return new ScheduleException(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("branch_id",UUID.class),r.getObject("employee_id",UUID.class),
                r.getDate("exception_date").toLocalDate(),ScheduleExceptionType.valueOf(r.getString("type")),start==null?null:start.toLocalTime(),end==null?null:end.toLocalTime(),
                r.getString("note"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
}
