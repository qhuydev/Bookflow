package com.bookflow.schedules.domain;

import java.time.*;
import java.util.UUID;

public record ScheduleException(UUID id, UUID businessId, UUID branchId, UUID employeeId,
                                LocalDate date, ScheduleExceptionType type,
                                LocalTime startLocalTime, LocalTime endLocalTime, String note,
                                Instant createdAt, Instant updatedAt) { }
