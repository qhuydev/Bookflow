package com.bookflow.bookings.api;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID bookingId,
        String status,
        UUID branchId,
        UUID employeeId,
        OffsetDateTime start,
        OffsetDateTime end,
        OffsetDateTime expiresAt,
        BigDecimal totalAmount,
        String currency,
        List<CreateBookingResponse.Item> items
) {
    public static BookingResponse from(Booking booking) {
        BookingItem first = booking.items().getFirst();
        int duration = booking.items().stream().mapToInt(BookingItem::durationMinutesSnapshot).sum();
        OffsetDateTime visibleStart = booking.startAt()
                .plusSeconds(first.bufferBeforeMinutesSnapshot() * 60L)
                .atOffset(ZoneOffset.UTC);
        return new BookingResponse(
                booking.id(), booking.status().name(), booking.branchId(), booking.employeeId(),
                visibleStart, visibleStart.plusMinutes(duration),
                booking.expiresAt() == null ? null : booking.expiresAt().atOffset(ZoneOffset.UTC),
                booking.totalAmount(), booking.currency(),
                booking.items().stream().map(item -> new CreateBookingResponse.Item(
                        item.serviceId(), item.serviceNameSnapshot(), item.priceSnapshot(),
                        item.currencySnapshot(), item.durationMinutesSnapshot(),
                        item.bufferBeforeMinutesSnapshot(), item.bufferAfterMinutesSnapshot()
                )).toList()
        );
    }
}
