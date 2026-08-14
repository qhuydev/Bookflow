package com.bookflow.schedules.api;

import com.bookflow.schedules.domain.ScheduleExceptionType;
import java.time.*;
import java.util.UUID;

public record ScheduleExceptionRequest(UUID branchId, LocalDate date, ScheduleExceptionType type,
                                       LocalTime startLocalTime, LocalTime endLocalTime, String note) { }
