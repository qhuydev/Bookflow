package com.bookflow.schedules.availability;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("bookflow.availability")
public record AvailabilityProperties(
        @Min(1) int slotStepMinutes,
        @Min(0) int defaultLeadTimeMinutes
) { }
