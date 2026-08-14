package com.bookflow.schedules.availability;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("test")
final class NoBookingBusyIntervalProvider implements BusyIntervalProvider {
    @Override
    public Map<UUID, List<TimeInterval>> findBusyIntervals(
            UUID businessId, UUID branchId, List<UUID> employeeIds, LocalDate date, ZoneId zoneId
    ) {
        return Map.of();
    }
}
