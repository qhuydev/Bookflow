package com.bookflow.bookings.application;

import com.bookflow.bookings.api.BookingResponse;
import com.bookflow.bookings.api.CancelBookingRequest;
import com.bookflow.bookings.api.RescheduleBookingRequest;
import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingItem;
import com.bookflow.bookings.domain.BookingRescheduleHistory;
import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.bookings.repository.BookingConstraintViolation;
import com.bookflow.bookings.repository.BookingRepository;
import com.bookflow.businesses.authorization.TenantAuthorizationService;
import com.bookflow.businesses.authorization.TenantPermission;
import com.bookflow.schedules.availability.AvailabilityQueryService;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;

@Service
@Profile("!test")
public class BookingLifecycleService {
    private static final EnumSet<BookingStatus> RESCHEDULABLE = EnumSet.of(
            BookingStatus.PENDING_PAYMENT, BookingStatus.PENDING_CONFIRMATION, BookingStatus.CONFIRMED
    );

    private final BookingRepository repository;
    private final BookingLifecycleValidator validator;
    private final TenantAuthorizationService authorization;
    private final AvailabilityQueryService availability;
    private final Clock clock;

    public BookingLifecycleService(
            BookingRepository repository,
            BookingLifecycleValidator validator,
            TenantAuthorizationService authorization,
            AvailabilityQueryService availability,
            Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.authorization = authorization;
        this.availability = availability;
        this.clock = clock;
    }

    @Transactional
    public void cancelAsCustomer(UUID userId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = lockCustomerBooking(userId, bookingId);
        cancel(booking, BookingStatus.CANCELLED_BY_CUSTOMER, userId, validator.cancelReason(request));
    }

    @Transactional
    public void cancelAsBusiness(
            UUID userId, UUID tenantId, UUID bookingId, CancelBookingRequest request
    ) {
        authorization.requirePermission(userId, tenantId, TenantPermission.BOOKING_MANAGE);
        Booking booking = lockTenantBooking(tenantId, bookingId);
        cancel(booking, BookingStatus.CANCELLED_BY_BUSINESS, userId, validator.cancelReason(request));
    }

    @Transactional
    public BookingResponse rescheduleAsCustomer(
            UUID userId, UUID bookingId, RescheduleBookingRequest request
    ) {
        Booking booking = lockCustomerBooking(userId, bookingId);
        return reschedule(booking, userId, validator.validate(request));
    }

    @Transactional
    public BookingResponse rescheduleAsBusiness(
            UUID userId, UUID tenantId, UUID bookingId, RescheduleBookingRequest request
    ) {
        authorization.requirePermission(userId, tenantId, TenantPermission.BOOKING_MANAGE);
        Booking booking = lockTenantBooking(tenantId, bookingId);
        return reschedule(booking, userId, validator.validate(request));
    }

    private void cancel(Booking booking, BookingStatus target, UUID actorUserId, String reason) {
        Booking transitioned = booking.transitionTo(target, actorUserId, reason, clock);
        if (!repository.updateStatus(
                booking.tenantId(), booking.id(), booking.status(), target, transitioned.updatedAt()
        )) {
            throw new BookingConflictException();
        }
        repository.insertHistory(transitioned.statusHistory().getLast());
    }

    private BookingResponse reschedule(
            Booking current,
            UUID actorUserId,
            BookingLifecycleValidator.ValidatedReschedule request
    ) {
        if (!RESCHEDULABLE.contains(current.status())) {
            throw new BookingConflictException();
        }
        if ((current.status() == BookingStatus.PENDING_PAYMENT
                || current.status() == BookingStatus.PENDING_CONFIRMATION)
                && current.expiresAt() != null && !current.expiresAt().isAfter(clock.instant())) {
            throw new BookingConflictException();
        }
        UUID employeeId = request.employeeId() == null ? current.employeeId() : request.employeeId();
        if (employeeId == null) {
            throw new ResourceNotFoundException();
        }
        BookingItem first = current.items().getFirst();
        BookingItem last = current.items().getLast();
        int durationMinutes = current.items().stream()
                .mapToInt(BookingItem::durationMinutesSnapshot).sum();
        var context = repository.findRescheduleContext(
                        current.tenantId(), current.branchId(), first.serviceId(), employeeId
                ).orElseThrow(ResourceNotFoundException::new);
        var requestedStart = request.start().toInstant();
        var date = requestedStart.atZone(ZoneId.of(context.timeZone())).toLocalDate();
        boolean exactSlot = availability.availability(
                        context.slug(), current.branchId(), first.serviceId(), employeeId, date, current.id(),
                        Duration.ofMinutes(durationMinutes),
                        Duration.ofMinutes(first.bufferBeforeMinutesSnapshot()),
                        Duration.ofMinutes(last.bufferAfterMinutesSnapshot())
                ).slots().stream()
                .anyMatch(slot -> slot.start().toInstant().equals(requestedStart)
                        && slot.employeeIds().contains(employeeId));
        if (!exactSlot) {
            throw new SlotUnavailableException();
        }

        var occupiedStart = requestedStart.minus(Duration.ofMinutes(first.bufferBeforeMinutesSnapshot()));
        var occupiedEnd = requestedStart.plus(Duration.ofMinutes(durationMinutes))
                .plus(Duration.ofMinutes(last.bufferAfterMinutesSnapshot()));
        if (employeeId.equals(current.employeeId())
                && occupiedStart.equals(current.startAt())
                && occupiedEnd.equals(current.endAt())) {
            return BookingResponse.from(current);
        }
        Booking changed = current.rescheduleTo(employeeId, occupiedStart, occupiedEnd, clock);
        try {
            if (!repository.updateSchedule(
                    current.tenantId(), current.id(), current.status(), current.updatedAt(), employeeId,
                    occupiedStart, occupiedEnd, changed.updatedAt()
            )) {
                throw new BookingConflictException();
            }
            repository.insertRescheduleHistory(new BookingRescheduleHistory(
                    UUID.randomUUID(), current.tenantId(), current.id(), current.employeeId(), employeeId,
                    current.startAt(), current.endAt(), occupiedStart, occupiedEnd, actorUserId,
                    request.reason(), changed.updatedAt()
            ));
        } catch (DataIntegrityViolationException exception) {
            if (BookingConstraintViolation.isSlotOverlap(exception)) {
                throw new SlotUnavailableException();
            }
            throw exception;
        }
        return BookingResponse.from(changed);
    }

    private Booking lockTenantBooking(UUID tenantId, UUID bookingId) {
        try {
            return repository.findByTenantAndIdForUpdate(tenantId, bookingId)
                    .orElseThrow(ResourceNotFoundException::new);
        } catch (PessimisticLockingFailureException exception) {
            throw new BookingConflictException();
        }
    }

    private Booking lockCustomerBooking(UUID userId, UUID bookingId) {
        try {
            return repository.findByCustomerUserAndIdForUpdate(userId, bookingId)
                    .orElseThrow(ResourceNotFoundException::new);
        } catch (PessimisticLockingFailureException exception) {
            throw new BookingConflictException();
        }
    }
}
