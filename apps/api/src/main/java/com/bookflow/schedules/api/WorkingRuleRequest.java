package com.bookflow.schedules.api;

import java.time.*;
import java.util.UUID;

public record WorkingRuleRequest(UUID branchId, DayOfWeek weekday, LocalTime startLocalTime,
                                 LocalTime endLocalTime, LocalDate effectiveFrom, LocalDate effectiveTo) { }
