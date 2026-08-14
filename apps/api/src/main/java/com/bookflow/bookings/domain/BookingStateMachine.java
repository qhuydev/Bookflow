package com.bookflow.bookings.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class BookingStateMachine {
    private static final Map<BookingStatus, EnumSet<BookingStatus>> TRANSITIONS = transitions();

    private BookingStateMachine() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return from != null && to != null && TRANSITIONS.get(from).contains(to);
    }

    public static void requireTransition(BookingStatus from, BookingStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidBookingTransitionException(from, to);
        }
    }

    private static Map<BookingStatus, EnumSet<BookingStatus>> transitions() {
        Map<BookingStatus, EnumSet<BookingStatus>> values = new EnumMap<>(BookingStatus.class);
        values.put(BookingStatus.PENDING_PAYMENT, EnumSet.of(
                BookingStatus.PENDING_CONFIRMATION,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_BUSINESS,
                BookingStatus.EXPIRED
        ));
        values.put(BookingStatus.PENDING_CONFIRMATION, EnumSet.of(
                BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_BUSINESS,
                BookingStatus.EXPIRED
        ));
        values.put(BookingStatus.CONFIRMED, EnumSet.of(
                BookingStatus.IN_PROGRESS,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_BUSINESS,
                BookingStatus.NO_SHOW
        ));
        values.put(BookingStatus.IN_PROGRESS, EnumSet.of(BookingStatus.COMPLETED));
        values.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));
        values.put(BookingStatus.CANCELLED_BY_CUSTOMER, EnumSet.noneOf(BookingStatus.class));
        values.put(BookingStatus.CANCELLED_BY_BUSINESS, EnumSet.noneOf(BookingStatus.class));
        values.put(BookingStatus.NO_SHOW, EnumSet.noneOf(BookingStatus.class));
        values.put(BookingStatus.EXPIRED, EnumSet.noneOf(BookingStatus.class));
        return Map.copyOf(values);
    }
}
