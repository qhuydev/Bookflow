package com.bookflow.bookings.domain;

public class InvalidBookingTransitionException extends RuntimeException {
    public InvalidBookingTransitionException(BookingStatus from, BookingStatus to) {
        super("Booking cannot transition from " + from + " to " + to + ".");
    }
}
