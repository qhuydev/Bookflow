package com.bookflow.bookings.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("bookflow.booking")
public record BookingProperties(
        @Min(1) int holdMinutes,
        @Min(1) int expiryBatchSize
) {
}
