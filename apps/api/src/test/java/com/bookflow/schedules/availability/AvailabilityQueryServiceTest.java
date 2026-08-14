package com.bookflow.schedules.availability;

import com.bookflow.schedules.availability.AvailabilityQueryRepository.ResourceContext;
import com.bookflow.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvailabilityQueryServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Test
    void rejectsInvalidPublicRelationshipBeforeCalculatingSlots() {
        AvailabilityQueryRepository repository = mock(AvailabilityQueryRepository.class);
        AvailabilityQueryService service = service(repository);
        UUID branch = UUID.randomUUID(), catalogService = UUID.randomUUID();
        when(repository.findResourceContext("salon", branch, catalogService)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.availability("  SALON  ", branch, catalogService, null, DATE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aRequestedEmployeeMustBeEligibleWhileAnEmptyAggregateIsAValidResult() {
        AvailabilityQueryRepository repository = mock(AvailabilityQueryRepository.class);
        UUID business = UUID.randomUUID(), branch = UUID.randomUUID(), catalogService = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        ResourceContext context = new ResourceContext(business, branch, catalogService, ZoneId.of("UTC"), 30, 60, 0, 0);
        when(repository.findResourceContext("salon", branch, catalogService)).thenReturn(Optional.of(context));
        when(repository.findEligibleEmployees(context, employee)).thenReturn(List.of());
        when(repository.findEligibleEmployees(context, null)).thenReturn(List.of());
        AvailabilityQueryService service = service(repository);

        assertThatThrownBy(() -> service.availability("salon", branch, catalogService, employee, DATE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.availability("salon", branch, catalogService, null, DATE).slots()).isEmpty();
    }

    private AvailabilityQueryService service(AvailabilityQueryRepository repository) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
        return new AvailabilityQueryService(repository, new AvailabilityEngine(clock),
                new AvailabilityProperties(15, 0),
                (business, branch, employees, date, zone) -> Map.of());
    }
}
