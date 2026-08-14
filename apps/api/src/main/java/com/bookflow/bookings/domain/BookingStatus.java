package com.bookflow.bookings.domain;

public enum BookingStatus {
    PENDING_PAYMENT(true),
    PENDING_CONFIRMATION(true),
    CONFIRMED(true),
    IN_PROGRESS(true),
    COMPLETED(false),
    CANCELLED_BY_CUSTOMER(false),
    CANCELLED_BY_BUSINESS(false),
    NO_SHOW(false),
    EXPIRED(false);

    private final boolean occupiesSlot;

    BookingStatus(boolean occupiesSlot) {
        this.occupiesSlot = occupiesSlot;
    }

    public boolean occupiesSlot() {
        return occupiesSlot;
    }

    public boolean isTerminal() {
        return !occupiesSlot;
    }
}
