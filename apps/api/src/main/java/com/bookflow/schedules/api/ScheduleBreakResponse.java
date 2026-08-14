package com.bookflow.schedules.api;

import com.bookflow.schedules.domain.ScheduleBreak;
import java.time.*;
import java.util.UUID;

public record ScheduleBreakResponse(UUID id, UUID businessId, UUID scheduleRuleId,
                                    LocalTime startLocalTime, LocalTime endLocalTime,
                                    Instant createdAt, Instant updatedAt) {
    public static ScheduleBreakResponse from(ScheduleBreak value) {
        return new ScheduleBreakResponse(value.id(), value.businessId(), value.scheduleRuleId(),
                value.startLocalTime(), value.endLocalTime(), value.createdAt(), value.updatedAt());
    }
}
