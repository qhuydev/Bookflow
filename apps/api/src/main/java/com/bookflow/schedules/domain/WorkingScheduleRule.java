package com.bookflow.schedules.domain;

import java.time.*;
import java.util.UUID;

public record WorkingScheduleRule(UUID id, UUID businessId, UUID branchId, UUID employeeId,
                                  DayOfWeek weekday, LocalTime startLocalTime, LocalTime endLocalTime,
                                  LocalDate effectiveFrom, LocalDate effectiveTo,
                                  Instant createdAt, Instant updatedAt) { }
