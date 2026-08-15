package com.bookflow.bookings.repository;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingRescheduleHistory;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.BookingStatusHistory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {
    void insertBooking(Booking booking);

    void insertItems(Booking booking);

    void insertHistory(BookingStatusHistory history);

    Optional<Booking> findByTenantAndId(UUID tenantId, UUID bookingId);

    Optional<Booking> findByTenantAndIdForUpdate(UUID tenantId, UUID bookingId);

    Optional<Booking> findByCustomerUserAndIdForUpdate(UUID userId, UUID bookingId);

    List<ExpiryCandidate> findExpiredCandidatesForUpdate(Instant now, int limit);

    boolean updateStatus(
            UUID tenantId,
            UUID bookingId,
            BookingStatus expectedStatus,
            BookingStatus newStatus,
            Instant updatedAt
    );

    boolean updateSchedule(
            UUID tenantId,
            UUID bookingId,
            BookingStatus expectedStatus,
            Instant expectedUpdatedAt,
            UUID employeeId,
            Instant startAt,
            Instant endAt,
            Instant updatedAt
    );

    void insertRescheduleHistory(BookingRescheduleHistory history);

    Optional<RescheduleContext> findRescheduleContext(
            UUID tenantId,
            UUID branchId,
            UUID serviceId,
            UUID employeeId
    );

    record ExpiryCandidate(UUID tenantId, UUID bookingId, BookingStatus status) {
    }

    record RescheduleContext(String slug, String timeZone) {
    }
}
