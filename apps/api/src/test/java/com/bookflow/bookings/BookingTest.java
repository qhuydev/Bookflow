package com.bookflow.bookings;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingCustomer;
import com.bookflow.bookings.domain.BookingItemSnapshot;
import com.bookflow.bookings.domain.BookingStateMachine;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.domain.InvalidBookingException;
import com.bookflow.bookings.domain.InvalidBookingTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createsAggregateWithMultipleImmutableSnapshotsAndInitialHistory() {
        Booking booking = booking();

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_CONFIRMATION);
        assertThat(booking.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(booking.totalAmount()).isEqualByComparingTo("150000.00");
        assertThat(booking.currency()).isEqualTo("VND");
        assertThat(booking.items()).hasSize(2);
        assertThat(booking.items().getFirst().serviceNameSnapshot()).isEqualTo("Cắt tóc");
        assertThat(booking.items().getFirst().priceSnapshot()).isEqualByComparingTo("100000.00");
        assertThat(booking.items().getFirst().durationMinutesSnapshot()).isEqualTo(60);
        assertThat(booking.items()).extracting(item -> item.position()).containsExactly(0, 1);
        assertThat(booking.statusHistory()).singleElement().satisfies(history -> {
            assertThat(history.fromStatus()).isNull();
            assertThat(history.toStatus()).isEqualTo(BookingStatus.PENDING_CONFIRMATION);
            assertThat(history.changedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void rejectsInvalidTimePriceDurationCurrencyAndCustomer() {
        Instant start = NOW.plus(Duration.ofDays(1));
        assertThatThrownBy(() -> Booking.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                customer(), start, start, snapshots(), Duration.ofMinutes(10), CLOCK
        )).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> new BookingItemSnapshot(
                UUID.randomUUID(), "Service", new BigDecimal("-1"), "VND", 30, 0, 0
        )).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> new BookingItemSnapshot(
                UUID.randomUUID(), "Service", BigDecimal.ONE, "VND", 0, 0, 0
        )).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> new BookingItemSnapshot(
                UUID.randomUUID(), "Service", BigDecimal.ONE, "VN", 30, 0, 0
        )).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> new BookingCustomer(null, "Guest", null, null))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void stateMachineAllowsOnlyDocumentedForwardTransitions() {
        Booking confirmed = booking().transitionTo(
                BookingStatus.CONFIRMED, UUID.randomUUID(), "Owner confirmed", laterClock(1)
        );
        Booking inProgress = confirmed.transitionTo(
                BookingStatus.IN_PROGRESS, UUID.randomUUID(), null, laterClock(2)
        );
        Booking completed = inProgress.transitionTo(
                BookingStatus.COMPLETED, UUID.randomUUID(), "Done", laterClock(3)
        );

        assertThat(completed.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(completed.statusHistory()).hasSize(4);
        assertThatThrownBy(() -> completed.transitionTo(
                BookingStatus.CONFIRMED, UUID.randomUUID(), null, laterClock(4)
        )).isInstanceOf(InvalidBookingTransitionException.class);
        assertThatThrownBy(() -> booking().transitionTo(
                BookingStatus.IN_PROGRESS, UUID.randomUUID(), null, laterClock(1)
        )).isInstanceOf(InvalidBookingTransitionException.class);

        assertThat(BookingStateMachine.canTransition(
                BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED
        )).isTrue();
        assertThat(BookingStateMachine.canTransition(
                BookingStatus.EXPIRED, BookingStatus.IN_PROGRESS
        )).isFalse();
    }

    @Test
    void occupiesSlotPolicyHasOneSourceOfTruthForEveryStatus() {
        assertThat(List.of(BookingStatus.values()).stream()
                .filter(BookingStatus::occupiesSlot)
                .toList()).containsExactly(
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.PENDING_CONFIRMATION,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS
        );
        assertThat(List.of(BookingStatus.values()).stream()
                .filter(BookingStatus::isTerminal)
                .toList()).containsExactly(
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_BUSINESS,
                BookingStatus.NO_SHOW,
                BookingStatus.EXPIRED
        );
    }

    private Booking booking() {
        Instant start = Instant.parse("2026-08-20T02:00:00Z");
        return Booking.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                customer(), start, start.plus(Duration.ofMinutes(90)), snapshots(),
                Duration.ofMinutes(10), CLOCK
        );
    }

    private BookingCustomer customer() {
        return new BookingCustomer(UUID.randomUUID(), "Khách hàng", "customer@example.test", null);
    }

    private List<BookingItemSnapshot> snapshots() {
        return List.of(
                new BookingItemSnapshot(
                        UUID.randomUUID(), "Cắt tóc", new BigDecimal("100000.00"),
                        "vnd", 60, 5, 10
                ),
                new BookingItemSnapshot(
                        UUID.randomUUID(), "Gội đầu", new BigDecimal("50000.00"),
                        "VND", 30, 0, 5
                )
        );
    }

    private Clock laterClock(long minutes) {
        return Clock.fixed(NOW.plus(Duration.ofMinutes(minutes)), ZoneOffset.UTC);
    }
}
