package com.bookflow.schedules.availability;

import java.time.*;
import java.util.*;

public record AvailabilityResult(LocalDate date, ZoneId zoneId, List<AvailabilitySlot> slots) {
    public AvailabilityResult { Objects.requireNonNull(date);Objects.requireNonNull(zoneId);slots=List.copyOf(Objects.requireNonNull(slots)); }
}
