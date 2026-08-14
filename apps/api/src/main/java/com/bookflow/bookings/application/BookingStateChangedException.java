package com.bookflow.bookings.application;

public class BookingStateChangedException extends RuntimeException {
    public BookingStateChangedException() {
        super("Booking state changed concurrently.");
    }
}
