package com.bookflow.bookings.api;

import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record CreateBookingResponse(
        UUID bookingId,
        String status,
        UUID branchId,
        UUID employeeId,
        OffsetDateTime start,
        OffsetDateTime end,
        OffsetDateTime expiresAt,
        BigDecimal totalAmount,
        String currency,
        List<Item> items
) {
    public static CreateBookingResponse from(Booking booking) {
        BookingItem first = booking.items().getFirst();
        OffsetDateTime visibleStart = booking.startAt()
                .plusSeconds(first.bufferBeforeMinutesSnapshot() * 60L)
                .atOffset(ZoneOffset.UTC);
        OffsetDateTime visibleEnd = visibleStart
                .plusMinutes(first.durationMinutesSnapshot());
        return new CreateBookingResponse(
                booking.id(), booking.status().name(), booking.branchId(), booking.employeeId(),
                visibleStart, visibleEnd, booking.expiresAt().atOffset(ZoneOffset.UTC),
                booking.totalAmount(), booking.currency(),
                booking.items().stream().map(Item::from).toList()
        );
    }

    public record Item(
            UUID serviceId,
            String name,
            BigDecimal price,
            String currency,
            int durationMinutes,
            int bufferBeforeMinutes,
            int bufferAfterMinutes
    ) {
        static Item from(BookingItem item) {
            return new Item(
                    item.serviceId(), item.serviceNameSnapshot(), item.priceSnapshot(),
                    item.currencySnapshot(), item.durationMinutesSnapshot(),
                    item.bufferBeforeMinutesSnapshot(), item.bufferAfterMinutesSnapshot()
            );
        }
    }
}
