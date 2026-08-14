package com.bookflow.schedules.availability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AvailabilityProperties.class)
class AvailabilityConfiguration {
    @Bean
    AvailabilityEngine availabilityEngine(Clock bookFlowClock) {
        return new AvailabilityEngine(bookFlowClock);
    }
}
