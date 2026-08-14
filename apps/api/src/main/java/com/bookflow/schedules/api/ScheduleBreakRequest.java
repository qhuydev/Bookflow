package com.bookflow.schedules.api;

import java.time.LocalTime;

public record ScheduleBreakRequest(LocalTime startLocalTime, LocalTime endLocalTime) { }
