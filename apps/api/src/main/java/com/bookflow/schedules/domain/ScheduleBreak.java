package com.bookflow.schedules.domain;

import java.time.*;
import java.util.UUID;

public record ScheduleBreak(UUID id, UUID businessId, UUID scheduleRuleId,
                            LocalTime startLocalTime, LocalTime endLocalTime,
                            Instant createdAt, Instant updatedAt) { }
