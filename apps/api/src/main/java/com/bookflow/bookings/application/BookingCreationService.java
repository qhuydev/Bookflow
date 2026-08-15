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
import java.util.Set;
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
                        validated.slug(), validated.branchId(), validated.serviceId())
                .orElseThrow(ResourceNotFoundException::new);
        String requestFingerprint = fingerprint.fingerprint(validated);
        Instant now = clock.instant();
        boolean claimed = creationRepository.claimIdempotencyKey(
                UUID.randomUUID(), context.tenantId(), validated.idempotencyKey(), requestFingerprint, now
        );
        if (!claimed) {
            return replay(context.tenantId(), validated.idempotencyKey(), requestFingerprint);
        }

        List<UUID> candidates = creationRepository.findEligibleEmployees(
                context.tenantId(), context.branchId(), context.serviceId(),
                validated.employeeId(), now
        );
        if (validated.employeeId() != null && candidates.isEmpty()) {
            throw new ResourceNotFoundException();
        }
        Set<UUID> availableEmployees = availableEmployeesAtSlot(validated, context);
        for (UUID employeeId : candidates) {
            if (!availableEmployees.contains(employeeId)) {
                continue;
            }
            Booking booking = newBooking(validated, context, employeeId);
            try {
                persistenceService.create(booking);
            } catch (DataIntegrityViolationException exception) {
                if (BookingConstraintViolation.isSlotOverlap(exception)) {
                    continue;
                }
                throw exception;
            }
            creationRepository.completeIdempotencyKey(
                    context.tenantId(), validated.idempotencyKey(), booking.id(), clock.instant()
            );
            return new Result(CreateBookingResponse.from(booking), false);
        }
        throw new SlotUnavailableException();
    }

    private Booking newBooking(
            ValidatedBookingRequest validated,
            CreationContext context,
            UUID employeeId
    ) {
        Instant visibleStart = validated.start().toInstant();
        Instant occupiedStart = visibleStart.minus(Duration.ofMinutes(context.bufferBeforeMinutes()));
        Instant occupiedEnd = visibleStart
                .plus(Duration.ofMinutes(context.durationMinutes()))
                .plus(Duration.ofMinutes(context.bufferAfterMinutes()));
        return Booking.create(
                UUID.randomUUID(), context.tenantId(), context.branchId(), employeeId,
                validated.customer(), occupiedStart, occupiedEnd,
                List.of(new BookingItemSnapshot(
                        context.serviceId(), context.serviceName(), context.price(), context.currency(),
                        context.durationMinutes(), context.bufferBeforeMinutes(), context.bufferAfterMinutes()
                )), Duration.ofMinutes(properties.holdMinutes()), clock
        );
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

    private Set<UUID> availableEmployeesAtSlot(
            ValidatedBookingRequest request,
            CreationContext context
    ) {
        LocalDate localDate = request.start().atZoneSameInstant(context.zoneId()).toLocalDate();
        var availability = availabilityService.availability(
                request.slug(), context.branchId(), context.serviceId(), request.employeeId(), localDate
        );
        return availability.slots().stream()
                .filter(slot -> slot.start().toInstant().equals(request.start().toInstant()))
                .findFirst()
                .map(slot -> Set.copyOf(slot.employeeIds()))
                .orElse(Set.of());
    }

    public record Result(CreateBookingResponse response, boolean replayed) {
    }
}
