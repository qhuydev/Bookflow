package com.bookflow.bookings.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "bookflow.booking", name = "expiry-enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryWorker {
    private final BookingExpiryService service;

    public BookingExpiryWorker(BookingExpiryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${bookflow.booking.expiry-interval:PT30S}")
    public void expireDueBookings() {
        service.expireDueBookings();
    }
}
