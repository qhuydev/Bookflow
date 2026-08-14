package com.bookflow.bookings.application;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException() {
        super("Booking was not found.");
    }
}
