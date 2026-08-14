package com.bookflow.bookings.repository;

import java.sql.SQLException;

public final class BookingConstraintViolation {
    public static final String SLOT_OVERLAP_CONSTRAINT = "no_overlapping_active_employee_bookings";

    private BookingConstraintViolation() {
    }

    public static boolean isSlotOverlap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23P01".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(SLOT_OVERLAP_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
