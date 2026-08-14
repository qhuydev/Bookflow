package com.bookflow.schedules.availability;

import java.time.Instant;
import java.util.Objects;

/** Customer-visible service interval. Buffers are intentionally not exposed as slot boundaries. */
public record AvailabilitySlot(Instant start, Instant end) {
    public AvailabilitySlot { Objects.requireNonNull(start);Objects.requireNonNull(end);if(!start.isBefore(end))throw new IllegalArgumentException("Slot start must be before end."); }
}
