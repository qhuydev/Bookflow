package com.bookflow.bookings.application;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException() {
        super("Booking state changed concurrently.");
    }
}
