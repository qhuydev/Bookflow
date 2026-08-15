package com.bookflow.bookings.repository;

import com.bookflow.bookings.domain.BookingStatus;
import com.bookflow.schedules.availability.BusyIntervalProvider;
import com.bookflow.schedules.availability.TimeInterval;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("!test")
public class BookingBusyIntervalProvider implements BusyIntervalProvider {
    private final NamedParameterJdbcTemplate jdbc;

    public BookingBusyIntervalProvider(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Map<UUID, List<TimeInterval>> findBusyIntervals(
            UUID businessId,
            UUID branchId,
            List<UUID> employeeIds,
            LocalDate date,
            ZoneId zoneId
    ) {
        return findBusyIntervals(businessId, branchId, employeeIds, date, zoneId, null);
    }

    @Override
    public Map<UUID, List<TimeInterval>> findBusyIntervals(
            UUID businessId,
            UUID branchId,
            List<UUID> employeeIds,
            LocalDate date,
            ZoneId zoneId,
            UUID excludedBookingId
    ) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TimeInterval>> result = new LinkedHashMap<>();
        employeeIds.forEach(employeeId -> result.put(employeeId, new ArrayList<>()));
        var dayStart = date.atStartOfDay(zoneId).toInstant();
        var dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<String> activeStatuses = java.util.Arrays.stream(BookingStatus.values())
                .filter(BookingStatus::occupiesSlot)
                .map(Enum::name)
                .toList();
        String sql = """
                SELECT employee_id, start_at, end_at
                FROM bookings
                WHERE tenant_id = :tenantId
                  AND branch_id = :branchId
                  AND employee_id IN (:employeeIds)
                  AND status IN (:activeStatuses)
                  AND start_at < :dayEnd
                  AND end_at > :dayStart
                  AND (CAST(:excludedBookingId AS uuid) IS NULL OR id <> :excludedBookingId)
                ORDER BY employee_id, start_at, end_at, id
                """;
        List<BusyRow> rows = jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("tenantId", businessId)
                        .addValue("branchId", branchId)
                        .addValue("employeeIds", employeeIds)
                        .addValue("activeStatuses", activeStatuses)
                        .addValue("excludedBookingId", excludedBookingId)
                        .addValue("dayStart", Timestamp.from(dayStart))
                        .addValue("dayEnd", Timestamp.from(dayEnd)),
                (resultSet, row) -> new BusyRow(
                        resultSet.getObject("employee_id", UUID.class),
                        new TimeInterval(
                                resultSet.getTimestamp("start_at").toInstant(),
                                resultSet.getTimestamp("end_at").toInstant()
                        )
                ));
        rows.forEach(row -> result.get(row.employeeId()).add(row.interval()));
        return result.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())
        ));
    }

    private record BusyRow(UUID employeeId, TimeInterval interval) {
    }
}
