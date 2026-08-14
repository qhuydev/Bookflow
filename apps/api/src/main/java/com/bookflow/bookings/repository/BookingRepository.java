package com.bookflow.bookings.repository;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.BookingStatusHistory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {
    void insertBooking(Booking booking);

    void insertItems(Booking booking);

    void insertHistory(BookingStatusHistory history);

    Optional<Booking> findByTenantAndId(UUID tenantId, UUID bookingId);

    boolean updateStatus(
            UUID tenantId,
            UUID bookingId,
            BookingStatus expectedStatus,
            BookingStatus newStatus,
            Instant updatedAt
    );
}
