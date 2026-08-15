package com.bookflow.bookings.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID employeeId,
        BookingCustomer customer,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant expiresAt,
        List<BookingItem> items,
        List<BookingStatusHistory> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {
    public Booking {
        requireIdentity(id, tenantId, branchId, customer);
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new InvalidBookingException("Booking start must be before end.");
        }
        if (status == null) {
            throw new InvalidBookingException("Booking status is required.");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidBookingException("Booking currency must contain three uppercase letters.");
        }
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new InvalidBookingException("Booking total cannot be negative.");
        }
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw new InvalidBookingException("Booking timestamps are invalid.");
        }
        if ((status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.PENDING_CONFIRMATION)
                && (expiresAt == null || !expiresAt.isAfter(createdAt))) {
            throw new InvalidBookingException("Pending booking requires a future expiry.");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidBookingException("Booking requires at least one service item.");
        }
        items = List.copyOf(items);
        if (items.stream().anyMatch(item -> !tenantId.equals(item.tenantId())
                || !id.equals(item.bookingId()) || !currency.equals(item.currencySnapshot()))) {
            throw new InvalidBookingException("Booking items must match booking identity, tenant and currency.");
        }
        BigDecimal itemTotal = items.stream()
                .map(BookingItem::priceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(itemTotal) != 0) {
            throw new InvalidBookingException("Booking total must equal item snapshots.");
        }
        if (statusHistory == null || statusHistory.isEmpty()) {
            throw new InvalidBookingException("Booking status history is required.");
        }
        statusHistory = List.copyOf(statusHistory);
        BookingStatusHistory latest = statusHistory.getLast();
        if (!tenantId.equals(latest.tenantId()) || !id.equals(latest.bookingId())
                || latest.toStatus() != status) {
            throw new InvalidBookingException("Booking status must match its latest history entry.");
        }
    }

    public static Booking create(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID employeeId,
            BookingCustomer customer,
            Instant startAt,
            Instant endAt,
            List<BookingItemSnapshot> snapshots,
            Duration holdDuration,
            Clock clock
    ) {
        requireIdentity(id, tenantId, branchId, customer);
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new InvalidBookingException("Booking start must be before end.");
        }
        if (snapshots == null || snapshots.isEmpty()) {
            throw new InvalidBookingException("Booking requires at least one service item.");
        }
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new InvalidBookingException("Booking hold duration must be positive.");
        }
        Objects.requireNonNull(clock, "clock");

        Instant now = clock.instant();
        String currency = snapshots.getFirst().currency();
        if (snapshots.stream().anyMatch(snapshot -> !currency.equals(snapshot.currency()))) {
            throw new InvalidBookingException("All booking items must use the same currency.");
        }

        List<BookingItem> items = new ArrayList<>(snapshots.size());
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < snapshots.size(); index++) {
            BookingItemSnapshot snapshot = snapshots.get(index);
            total = total.add(snapshot.price());
            items.add(new BookingItem(
                    UUID.randomUUID(), tenantId, id, snapshot.serviceId(), index,
                    snapshot.serviceName(), snapshot.price(), snapshot.currency(),
                    snapshot.durationMinutes(), snapshot.bufferBeforeMinutes(),
                    snapshot.bufferAfterMinutes(), now
            ));
        }

        BookingStatus initialStatus = BookingStatus.PENDING_CONFIRMATION;
        BookingStatusHistory initialHistory = new BookingStatusHistory(
                UUID.randomUUID(), tenantId, id, null, initialStatus, customer.userId(),
                "Booking created", now
        );
        return new Booking(
                id, tenantId, branchId, employeeId, customer, startAt, endAt,
                initialStatus, currency.toUpperCase(Locale.ROOT), total,
                now.plus(holdDuration), List.copyOf(items), List.of(initialHistory), now, now
        );
    }

    public Booking transitionTo(BookingStatus target, UUID actorUserId, String reason, Clock clock) {
        BookingStateMachine.requireTransition(status, target);
        Instant clockInstant = Objects.requireNonNull(clock, "clock").instant();
        Instant changedAt = clockInstant.isAfter(updatedAt) ? clockInstant : updatedAt.plusNanos(1_000);
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.strip();
        List<BookingStatusHistory> history = new ArrayList<>(statusHistory);
        history.add(new BookingStatusHistory(
                UUID.randomUUID(), tenantId, id, status, target, actorUserId,
                normalizedReason, changedAt
        ));
        return new Booking(
                id, tenantId, branchId, employeeId, customer, startAt, endAt,
                target, currency, totalAmount, expiresAt, items,
                List.copyOf(history), createdAt, changedAt
        );
    }

    public Booking rescheduleTo(UUID targetEmployeeId, Instant targetStartAt, Instant targetEndAt, Clock clock) {
        if (targetEmployeeId == null) {
            throw new InvalidBookingException("Rescheduled booking requires an employee.");
        }
        if (targetStartAt == null || targetEndAt == null || !targetStartAt.isBefore(targetEndAt)) {
            throw new InvalidBookingException("Rescheduled booking start must be before end.");
        }
        Instant clockInstant = Objects.requireNonNull(clock, "clock").instant();
        Instant changedAt = clockInstant.isAfter(updatedAt) ? clockInstant : updatedAt.plusNanos(1_000);
        return new Booking(
                id, tenantId, branchId, targetEmployeeId, customer, targetStartAt, targetEndAt,
                status, currency, totalAmount, expiresAt, items, statusHistory, createdAt, changedAt
        );
    }

    private static void requireIdentity(UUID id, UUID tenantId, UUID branchId, BookingCustomer customer) {
        if (id == null || tenantId == null || branchId == null || customer == null) {
            throw new InvalidBookingException("Booking identity, tenant, branch and customer are required.");
        }
    }
}
