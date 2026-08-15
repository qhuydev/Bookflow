package com.bookflow.schedules.availability;

import com.bookflow.schedules.availability.AvailabilityQueryRepository.ResourceContext;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Service
@Profile("!test")
public class AvailabilityQueryService {
    private final AvailabilityQueryRepository repository;
    private final AvailabilityEngine engine;
    private final AvailabilityProperties properties;
    private final BusyIntervalProvider busyIntervals;

    public AvailabilityQueryService(AvailabilityQueryRepository repository, AvailabilityEngine engine,
                                    AvailabilityProperties properties, BusyIntervalProvider busyIntervals) {
        this.repository = repository;
        this.engine = engine;
        this.properties = properties;
        this.busyIntervals = busyIntervals;
    }

    @Transactional(readOnly = true)
    public PublicAvailability availability(
            String rawSlug, UUID branchId, UUID serviceId, UUID employeeId, LocalDate date
    ) {
        return availability(rawSlug, branchId, serviceId, employeeId, date, null);
    }

    @Transactional(readOnly = true)
    public PublicAvailability availability(
            String rawSlug, UUID branchId, UUID serviceId, UUID employeeId, LocalDate date,
            UUID excludedBookingId
    ) {
        return availability(rawSlug, branchId, serviceId, employeeId, date, excludedBookingId,
                null, null, null);
    }

    @Transactional(readOnly = true)
    public PublicAvailability availability(
            String rawSlug, UUID branchId, UUID serviceId, UUID employeeId, LocalDate date,
            UUID excludedBookingId, Duration durationOverride,
            Duration bufferBeforeOverride, Duration bufferAfterOverride
    ) {
        String slug = rawSlug.strip().toLowerCase(Locale.ROOT);
        ResourceContext context = repository.findResourceContext(slug, branchId, serviceId)
                .orElseThrow(ResourceNotFoundException::new);
        List<UUID> employees = repository.findEligibleEmployees(context, employeeId);
        if (employeeId != null && employees.isEmpty()) throw new ResourceNotFoundException();
        if (employees.isEmpty()) return response(context, date, List.of());

        var rules = repository.findWorkingRules(context, employees, date);
        var breaks = repository.findBreaks(context.businessId(), rules.stream().map(value -> value.id()).toList());
        var exceptions = repository.findExceptions(context, employees, date);
        Map<UUID, List<TimeInterval>> busy = busyIntervals.findBusyIntervals(
                context.businessId(), context.branchId(), employees, date, context.zoneId(), excludedBookingId);

        Map<SlotKey, TreeSet<UUID>> aggregated = new TreeMap<>();
        for (UUID id : employees) {
            AvailabilityResult result = engine.calculate(new AvailabilityInput(
                    context.branchId(), id, date, context.zoneId(), rules, breaks, exceptions,
                    busy.getOrDefault(id, List.of()),
                    durationOverride == null ? Duration.ofMinutes(context.durationMinutes()) : durationOverride,
                    bufferBeforeOverride == null ? Duration.ofMinutes(context.bufferBeforeMinutes()) : bufferBeforeOverride,
                    bufferAfterOverride == null ? Duration.ofMinutes(context.bufferAfterMinutes()) : bufferAfterOverride,
                    Duration.ofMinutes(properties.defaultLeadTimeMinutes()), context.maxBookingAdvanceDays(),
                    Duration.ofMinutes(properties.slotStepMinutes())));
            for (AvailabilitySlot slot : result.slots()) {
                aggregated.computeIfAbsent(new SlotKey(slot.start(), slot.end()), ignored -> new TreeSet<>()).add(id);
            }
        }
        List<PublicAvailabilitySlot> slots = new ArrayList<>();
        aggregated.forEach((key, ids) -> slots.add(new PublicAvailabilitySlot(
                key.start().atZone(context.zoneId()).toOffsetDateTime(),
                key.end().atZone(context.zoneId()).toOffsetDateTime(), List.copyOf(ids))));
        return response(context, date, slots);
    }

    private PublicAvailability response(ResourceContext context, LocalDate date, List<PublicAvailabilitySlot> slots) {
        return new PublicAvailability(date, context.zoneId().getId(), context.branchId(), context.serviceId(), slots);
    }

    private record SlotKey(java.time.Instant start, java.time.Instant end) implements Comparable<SlotKey> {
        @Override public int compareTo(SlotKey other) {
            int startOrder = start.compareTo(other.start);
            return startOrder != 0 ? startOrder : end.compareTo(other.end);
        }
    }

    public record PublicAvailability(LocalDate date, String timeZone, UUID branchId, UUID serviceId,
                                     List<PublicAvailabilitySlot> slots) { }
    public record PublicAvailabilitySlot(OffsetDateTime start, OffsetDateTime end, List<UUID> employeeIds) { }
}
