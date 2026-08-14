package com.bookflow.schedules.availability;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Integration boundary for a future booking-backed busy interval adapter. */
public interface BusyIntervalProvider {
    Map<UUID, List<TimeInterval>> findBusyIntervals(
            UUID businessId,
            UUID branchId,
            List<UUID> employeeIds,
            LocalDate date,
            ZoneId zoneId
    );
}
