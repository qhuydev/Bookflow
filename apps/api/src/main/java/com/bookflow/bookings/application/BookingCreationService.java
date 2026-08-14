package com.bookflow.bookings.application;

import com.bookflow.bookings.api.CreateBookingRequest;
import com.bookflow.bookings.api.CreateBookingResponse;
import com.bookflow.bookings.application.BookingRequestValidator.ValidatedBookingRequest;
import com.bookflow.bookings.config.BookingProperties;
import com.bookflow.bookings.domain.Booking;
import com.bookflow.bookings.domain.BookingItemSnapshot;
import com.bookflow.bookings.repository.BookingConstraintViolation;
import com.bookflow.bookings.repository.BookingCreationRepository;
import com.bookflow.bookings.repository.BookingCreationRepository.CreationContext;
import com.bookflow.schedules.availability.AvailabilityQueryService;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!test")
public class BookingCreationService {
    private final BookingRequestValidator validator;
    private final BookingRequestFingerprint fingerprint;
    private final BookingCreationRepository creationRepository;
    private final BookingPersistenceService persistenceService;
    private final AvailabilityQueryService availabilityService;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingCreationService(
            BookingRequestValidator validator,
            BookingRequestFingerprint fingerprint,
            BookingCreationRepository creationRepository,
            BookingPersistenceService persistenceService,
            AvailabilityQueryService availabilityService,
            BookingProperties properties,
            Clock clock
    ) {
        this.validator = validator;
        this.fingerprint = fingerprint;
        this.creationRepository = creationRepository;
        this.persistenceService = persistenceService;
        this.availabilityService = availabilityService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Result create(String slug, String idempotencyKey, CreateBookingRequest request) {
        ValidatedBookingRequest validated = validator.validate(slug, idempotencyKey, request);
        CreationContext context = creationRepository.findCreationContext(
                        validated.slug(), validated.branchId(), validated.serviceId(), validated.employeeId())
                .orElseThrow(ResourceNotFoundException::new);
        String requestFingerprint = fingerprint.fingerprint(validated);
        Instant now = clock.instant();
        boolean claimed = creationRepository.claimIdempotencyKey(
                UUID.randomUUID(), context.tenantId(), validated.idempotencyKey(), requestFingerprint, now
        );
        if (!claimed) {
            return replay(context.tenantId(), validated.idempotencyKey(), requestFingerprint);
        }

        recheckAvailability(validated, context);
        Instant visibleStart = validated.start().toInstant();
        Instant occupiedStart = visibleStart.minus(Duration.ofMinutes(context.bufferBeforeMinutes()));
        Instant occupiedEnd = visibleStart
                .plus(Duration.ofMinutes(context.durationMinutes()))
                .plus(Duration.ofMinutes(context.bufferAfterMinutes()));
        Booking booking = Booking.create(
                UUID.randomUUID(), context.tenantId(), context.branchId(), context.employeeId(),
                validated.customer(), occupiedStart, occupiedEnd,
                List.of(new BookingItemSnapshot(
                        context.serviceId(), context.serviceName(), context.price(), context.currency(),
                        context.durationMinutes(), context.bufferBeforeMinutes(), context.bufferAfterMinutes()
                )), Duration.ofMinutes(properties.holdMinutes()), clock
        );
        try {
            persistenceService.create(booking);
        } catch (DataIntegrityViolationException exception) {
            if (BookingConstraintViolation.isSlotOverlap(exception)) {
                throw new SlotUnavailableException();
            }
            throw exception;
        }
        creationRepository.completeIdempotencyKey(
                context.tenantId(), validated.idempotencyKey(), booking.id(), clock.instant()
        );
        return new Result(CreateBookingResponse.from(booking), false);
    }

    private Result replay(UUID tenantId, String key, String requestFingerprint) {
        var record = creationRepository.findIdempotencyKeyForUpdate(tenantId, key)
                .orElseThrow(() -> new IllegalStateException("Claimed idempotency record is missing."));
        if (!record.fingerprint().equals(requestFingerprint)) {
            throw new IdempotencyKeyReusedException();
        }
        if (record.bookingId() == null) {
            throw new IllegalStateException("Committed idempotency record is incomplete.");
        }
        return new Result(
                CreateBookingResponse.from(persistenceService.find(tenantId, record.bookingId())), true
        );
    }

    private void recheckAvailability(ValidatedBookingRequest request, CreationContext context) {
        LocalDate localDate = request.start().atZoneSameInstant(context.zoneId()).toLocalDate();
        var availability = availabilityService.availability(
                request.slug(), context.branchId(), context.serviceId(), context.employeeId(), localDate
        );
        boolean available = availability.slots().stream().anyMatch(slot ->
                slot.start().toInstant().equals(request.start().toInstant())
                        && slot.employeeIds().contains(context.employeeId())
        );
        if (!available) {
            throw new SlotUnavailableException();
        }
    }

    public record Result(CreateBookingResponse response, boolean replayed) {
    }
}
