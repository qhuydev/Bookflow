package com.bookflow.schedules.repository;

import com.bookflow.schedules.application.ScheduleRequestValidator.*;
import com.bookflow.schedules.domain.*;
import java.util.*;

public interface ScheduleRepository {
    void lockRuleKey(UUID tenantId, UUID employeeId, UUID branchId, java.time.DayOfWeek weekday);
    boolean ruleOverlaps(UUID tenantId, UUID employeeId, RuleValues values, UUID excludedId);
    WorkingScheduleRule createRule(UUID tenantId, UUID employeeId, RuleValues values);
    List<WorkingScheduleRule> listRules(UUID tenantId, UUID employeeId);
    Optional<WorkingScheduleRule> findRule(UUID tenantId, UUID employeeId, UUID ruleId);
    Optional<WorkingScheduleRule> updateRule(UUID tenantId, UUID employeeId, UUID ruleId, RuleValues values);
    boolean deleteRule(UUID tenantId, UUID employeeId, UUID ruleId);

    boolean breakOverlaps(UUID tenantId, UUID ruleId, BreakValues values, UUID excludedId);
    ScheduleBreak createBreak(UUID tenantId, UUID ruleId, BreakValues values);
    List<ScheduleBreak> listBreaks(UUID tenantId, UUID ruleId);
    Optional<ScheduleBreak> findBreak(UUID tenantId, UUID ruleId, UUID breakId);
    Optional<ScheduleBreak> updateBreak(UUID tenantId, UUID ruleId, UUID breakId, BreakValues values);
    boolean deleteBreak(UUID tenantId, UUID ruleId, UUID breakId);

    ScheduleException createException(UUID tenantId, UUID employeeId, ExceptionValues values);
    List<ScheduleException> listExceptions(UUID tenantId, UUID employeeId);
    Optional<ScheduleException> findException(UUID tenantId, UUID employeeId, UUID exceptionId);
    Optional<ScheduleException> updateException(UUID tenantId, UUID employeeId, UUID exceptionId, ExceptionValues values);
    boolean deleteException(UUID tenantId, UUID employeeId, UUID exceptionId);
}
