package com.bookflow.bookings.application;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.BookingStatusHistory;
import com.bookflow.bookings.repository.BookingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!test")
public class BookingPersistenceService {
    private final BookingRepository repository;
    private final Clock clock;

    public BookingPersistenceService(BookingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Booking create(Booking booking) {
        repository.insertBooking(booking);
        repository.insertItems(booking);
        for (BookingStatusHistory history : booking.statusHistory()) {
            repository.insertHistory(history);
        }
        return booking;
    }

    public Booking find(UUID tenantId, UUID bookingId) {
        return repository.findByTenantAndId(tenantId, bookingId)
                .orElseThrow(BookingNotFoundException::new);
    }

    @Transactional
    public Booking transition(
            UUID tenantId,
            UUID bookingId,
            BookingStatus target,
            UUID actorUserId,
            String reason
    ) {
        Booking current = find(tenantId, bookingId);
        Booking transitioned = current.transitionTo(target, actorUserId, reason, clock);
        if (!repository.updateStatus(
                tenantId, bookingId, current.status(), transitioned.status(), transitioned.updatedAt()
        )) {
            throw new BookingStateChangedException();
        }
        repository.insertHistory(transitioned.statusHistory().getLast());
        return transitioned;
    }
}
