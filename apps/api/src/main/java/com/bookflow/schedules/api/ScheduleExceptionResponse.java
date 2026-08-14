package com.bookflow.schedules.api;

import com.bookflow.schedules.domain.*;
import java.time.*;
import java.util.UUID;

public record ScheduleExceptionResponse(UUID id, UUID businessId, UUID branchId, UUID employeeId,
                                        LocalDate date, ScheduleExceptionType type,
                                        LocalTime startLocalTime, LocalTime endLocalTime, String note,
                                        Instant createdAt, Instant updatedAt) {
    public static ScheduleExceptionResponse from(ScheduleException value) {
        return new ScheduleExceptionResponse(value.id(), value.businessId(), value.branchId(), value.employeeId(),
                value.date(), value.type(), value.startLocalTime(), value.endLocalTime(), value.note(),
                value.createdAt(), value.updatedAt());
    }
}
