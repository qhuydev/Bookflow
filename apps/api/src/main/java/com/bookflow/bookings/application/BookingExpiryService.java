package com.bookflow.bookings.application;

import com.bookflow.bookings.config.BookingProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class BookingExpiryService {
    private final BookingExpiryBatchProcessor processor;
    private final BookingProperties properties;

    public BookingExpiryService(BookingExpiryBatchProcessor processor, BookingProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    public int expireDueBookings() {
        int total = 0;
        BookingExpiryBatchProcessor.BatchResult result;
        do {
            result = processor.expireBatch(properties.expiryBatchSize());
            total += result.expired();
        } while (result.selected() == properties.expiryBatchSize());
        return total;
    }
}
