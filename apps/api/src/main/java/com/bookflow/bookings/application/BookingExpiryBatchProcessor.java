package com.bookflow.bookings.application;

import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.BookingStatusHistory;
import com.bookflow.bookings.domain.BookingStateMachine;
import com.bookflow.bookings.repository.BookingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Profile("!test")
public class BookingExpiryBatchProcessor {
    private final BookingRepository repository;
    private final Clock clock;

    public BookingExpiryBatchProcessor(BookingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public BatchResult expireBatch(int batchSize) {
        Instant now = clock.instant();
        var candidates = repository.findExpiredCandidatesForUpdate(now, batchSize);
        int expired = 0;
        for (var candidate : candidates) {
            BookingStateMachine.requireTransition(candidate.status(), BookingStatus.EXPIRED);
            if (repository.updateStatus(
                    candidate.tenantId(), candidate.bookingId(), candidate.status(), BookingStatus.EXPIRED, now
            )) {
                repository.insertHistory(new BookingStatusHistory(
                        UUID.randomUUID(), candidate.tenantId(), candidate.bookingId(), candidate.status(),
                        BookingStatus.EXPIRED, null, "Booking hold expired", now
                ));
                expired++;
            }
        }
        return new BatchResult(candidates.size(), expired);
    }

    public record BatchResult(int selected, int expired) {
    }
}
