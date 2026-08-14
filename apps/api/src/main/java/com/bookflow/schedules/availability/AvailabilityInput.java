package com.bookflow.schedules.availability;

import com.bookflow.schedules.domain.*;
import java.time.*;
import java.util.*;

public record AvailabilityInput(UUID branchId, UUID employeeId, LocalDate date, ZoneId zoneId,
                                List<WorkingScheduleRule> workingRules, List<ScheduleBreak> breaks,
                                List<ScheduleException> exceptions, List<TimeInterval> busyIntervals,
                                Duration serviceDuration, Duration bufferBefore, Duration bufferAfter,
                                Duration leadTime, int maxBookingAdvanceDays, Duration slotStep) {
    public AvailabilityInput {
        Objects.requireNonNull(branchId);Objects.requireNonNull(employeeId);Objects.requireNonNull(date);Objects.requireNonNull(zoneId);
        workingRules=List.copyOf(Objects.requireNonNull(workingRules));breaks=List.copyOf(Objects.requireNonNull(breaks));
        exceptions=List.copyOf(Objects.requireNonNull(exceptions));busyIntervals=List.copyOf(Objects.requireNonNull(busyIntervals));
        Objects.requireNonNull(serviceDuration);Objects.requireNonNull(bufferBefore);Objects.requireNonNull(bufferAfter);Objects.requireNonNull(leadTime);Objects.requireNonNull(slotStep);
        if(serviceDuration.isZero()||serviceDuration.isNegative())throw new IllegalArgumentException("Service duration must be positive.");
        if(bufferBefore.isNegative()||bufferAfter.isNegative()||leadTime.isNegative())throw new IllegalArgumentException("Buffers and lead time must not be negative.");
        if(slotStep.isZero()||slotStep.isNegative()||slotStep.toNanosPart()!=0)throw new IllegalArgumentException("Slot step must be a positive whole-second duration.");
        if(maxBookingAdvanceDays<0)throw new IllegalArgumentException("Booking horizon must not be negative.");
    }
}
