package com.bookflow.schedules.api;

import com.bookflow.schedules.domain.WorkingScheduleRule;
import java.time.*;
import java.util.UUID;

public record WorkingRuleResponse(UUID id, UUID businessId, UUID branchId, UUID employeeId,
                                  DayOfWeek weekday, LocalTime startLocalTime, LocalTime endLocalTime,
                                  LocalDate effectiveFrom, LocalDate effectiveTo,
                                  Instant createdAt, Instant updatedAt) {
    public static WorkingRuleResponse from(WorkingScheduleRule value) {
        return new WorkingRuleResponse(value.id(), value.businessId(), value.branchId(), value.employeeId(),
                value.weekday(), value.startLocalTime(), value.endLocalTime(), value.effectiveFrom(),
                value.effectiveTo(), value.createdAt(), value.updatedAt());
    }
}
