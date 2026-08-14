package com.bookflow.schedules.availability;

import com.bookflow.schedules.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AvailabilityEngineTest {
    private static final UUID BUSINESS=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_BRANCH=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID EMPLOYEE=UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final LocalDate MONDAY=LocalDate.of(2026,8,17);
    private static final ZoneId UTC=ZoneOffset.UTC;
    private static final Instant CREATED=Instant.parse("2026-01-01T00:00:00Z");

    @Test void normalShiftGeneratesFifteenMinuteGrid(){
        var result=engine("2026-08-17T00:00:00Z").calculate(input(MONDAY,UTC,List.of(rule(BRANCH,MONDAY,"09:00","18:00")),List.of(),List.of(),List.of(),60,0,0,0,30,15));
        assertThat(result.slots()).hasSize(33);assertThat(times(result)).startsWith(LocalTime.of(9,0),LocalTime.of(9,15)).endsWith(LocalTime.of(17,0));
    }
    @Test void splitShiftNeverCreatesSlotAcrossGap(){
        var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","12:00"),rule(BRANCH,MONDAY,"13:00","18:00")),List.of(),List.of(),List.of(),60,0,0);
        assertThat(times(result)).doesNotContain(LocalTime.of(11,15),LocalTime.of(11,30),LocalTime.of(11,45),LocalTime.of(12,0),LocalTime.of(12,15),LocalTime.of(12,30),LocalTime.of(12,45));
    }
    @Test void breaksAreSubtractedFromTheirRule(){
        var rule=rule(BRANCH,MONDAY,"09:00","18:00");var result=calculate(List.of(rule),List.of(scheduleBreak(rule,"12:00","13:00")),List.of(),List.of(),60,0,0);
        assertThat(result.slots()).allMatch(slot->!occupied(slot,0,0).overlaps(at("12:00","13:00")));
    }
    @Test void serviceLongerThanFreeIntervalHasNoSlot(){assertThat(calculate(List.of(rule(BRANCH,MONDAY,"09:00","09:30")),List.of(),List.of(),List.of(),60,0,0).slots()).isEmpty();}
    @Test void bufferBeforeMustFitInsideFreeInterval(){
        var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","12:00")),List.of(),List.of(),List.of(),60,15,0);
        assertThat(times(result)).startsWith(LocalTime.of(9,15)).doesNotContain(LocalTime.of(9,0));
    }
    @Test void bufferAfterMustFitInsideFreeInterval(){
        var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","12:00")),List.of(),List.of(),List.of(),60,0,15);
        assertThat(times(result)).endsWith(LocalTime.of(10,45)).doesNotContain(LocalTime.of(11,0));
    }
    @Test void partialTimeOffIsSubtractedAfterOverrides(){
        var off=exception(BRANCH,MONDAY,ScheduleExceptionType.TIME_OFF,"14:00","16:00");var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","18:00")),List.of(),List.of(off),List.of(),60,0,0);
        assertThat(result.slots()).allMatch(slot->!occupied(slot,0,0).overlaps(at("14:00","16:00")));
    }
    @Test void fullDayTimeOffWinsOverNormalAndOverride(){
        var full=exception(BRANCH,MONDAY,ScheduleExceptionType.TIME_OFF,null,null);var override=exception(BRANCH,MONDAY,ScheduleExceptionType.WORKING_OVERRIDE,"18:00","21:00");
        assertThat(calculate(List.of(rule(BRANCH,MONDAY,"09:00","18:00")),List.of(),List.of(full,override),List.of(),60,0,0).slots()).isEmpty();
    }
    @Test void workingOverrideCreatesAvailabilityWithoutNormalRule(){
        var override=exception(BRANCH,MONDAY,ScheduleExceptionType.WORKING_OVERRIDE,"18:00","21:00");
        assertThat(times(calculate(List.of(),List.of(),List.of(override),List.of(),60,0,0))).startsWith(LocalTime.of(18,0)).endsWith(LocalTime.of(20,0));
    }
    @Test void busyIntervalsAreSubtracted(){
        var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","18:00")),List.of(),List.of(),List.of(at("10:00","11:00"),at("15:00","16:00")),60,0,0);
        assertThat(result.slots()).allMatch(slot->!occupied(slot,0,0).overlaps(at("10:00","11:00"))&&!occupied(slot,0,0).overlaps(at("15:00","16:00")));
    }
    @Test void touchingBusyBoundaryIsAllowed(){
        var result=calculate(List.of(rule(BRANCH,MONDAY,"09:00","12:00")),List.of(),List.of(),List.of(at("10:00","11:00")),60,0,0);
        assertThat(times(result)).containsExactly(LocalTime.of(9,0),LocalTime.of(11,0));
    }
    @Test void leadTimeUsesCustomerStartAndRoundsToGrid(){
        var base=input(MONDAY,UTC,List.of(rule(BRANCH,MONDAY,"10:00","13:00")),List.of(),List.of(),List.of(),30,0,0,60,30,15);
        assertThat(times(engine("2026-08-17T10:07:00Z").calculate(base)).getFirst()).isEqualTo(LocalTime.of(11,15));
        assertThat(times(engine("2026-08-17T10:00:00Z").calculate(base)).getFirst()).isEqualTo(LocalTime.of(11,0));
        assertThat(times(engine("2026-08-17T10:01:00Z").calculate(base)).getFirst()).isEqualTo(LocalTime.of(11,15));
    }
    @Test void bookingHorizonIsInclusiveAtBoundary(){
        LocalDate today=LocalDate.of(2026,8,1),boundary=today.plusDays(30),outside=today.plusDays(31);Clock clock=Clock.fixed(today.atStartOfDay(UTC).toInstant(),UTC);
        assertThat(new AvailabilityEngine(clock).calculate(input(boundary,UTC,List.of(rule(BRANCH,boundary,"09:00","10:00")),List.of(),List.of(),List.of(),30,0,0,0,30,15)).slots()).isNotEmpty();
        assertThat(new AvailabilityEngine(clock).calculate(input(outside,UTC,List.of(rule(BRANCH,outside,"09:00","10:00")),List.of(),List.of(),List.of(),30,0,0,0,30,15)).slots()).isEmpty();
        assertThat(new AvailabilityEngine(clock).calculate(input(today.minusDays(1),UTC,List.of(rule(BRANCH,today.minusDays(1),"09:00","10:00")),List.of(),List.of(),List.of(),30,0,0,0,30,15)).slots()).isEmpty();
    }
    @Test void multipleUnsortedBreaksAndBusyIntervalsRemainDeterministic(){
        var rule=rule(BRANCH,MONDAY,"09:00","18:00");var input=input(MONDAY,UTC,List.of(rule),List.of(scheduleBreak(rule,"15:00","15:30"),scheduleBreak(rule,"12:00","13:00")),List.of(),List.of(at("16:00","17:00"),at("10:00","11:00")),30,0,0,0,30,15);
        assertThat(engine("2026-08-17T00:00:00Z").calculate(input)).isEqualTo(engine("2026-08-17T00:00:00Z").calculate(input));
    }
    @Test void emptyScheduleProducesNoSlots(){assertThat(calculate(List.of(),List.of(),List.of(),List.of(),60,0,0).slots()).isEmpty();}
    @Test void engineFiltersRulesAndExceptionsToRequestedBranchAndEmployee(){
        var otherRule=rule(OTHER_BRANCH,MONDAY,"06:00","23:00");var own=rule(BRANCH,MONDAY,"09:00","10:00");var foreignOff=exception(OTHER_BRANCH,MONDAY,ScheduleExceptionType.TIME_OFF,null,null);
        assertThat(times(calculate(List.of(otherRule,own),List.of(),List.of(foreignOff),List.of(),30,0,0))).containsExactly(LocalTime.of(9,0),LocalTime.of(9,15),LocalTime.of(9,30));
    }
    @Test void dstGapShiftsForwardAndNeverDuplicatesInstantSlots(){
        ZoneId zone=ZoneId.of("America/New_York");LocalDate date=LocalDate.of(2026,3,8);var result=engine("2026-03-07T12:00:00Z").calculate(input(date,zone,List.of(rule(BRANCH,date,"01:00","04:00")),List.of(),List.of(),List.of(),30,0,0,0,2,30));
        assertThat(result.slots()).extracting(AvailabilitySlot::start).containsExactly(
                Instant.parse("2026-03-08T06:00:00Z"),Instant.parse("2026-03-08T06:30:00Z"),
                Instant.parse("2026-03-08T07:00:00Z"),Instant.parse("2026-03-08T07:30:00Z"));
    }
    @Test void dstOverlapUsesEarlierOffsetWithoutDuplicateAmbiguousSlots(){
        ZoneId zone=ZoneId.of("America/New_York");LocalDate date=LocalDate.of(2026,11,1);var result=engine("2026-10-31T12:00:00Z").calculate(input(date,zone,List.of(rule(BRANCH,date,"01:00","03:00")),List.of(),List.of(),List.of(),30,0,0,0,2,30));
        assertThat(result.slots()).extracting(AvailabilitySlot::start).containsExactly(
                Instant.parse("2026-11-01T05:00:00Z"),Instant.parse("2026-11-01T05:30:00Z"),
                Instant.parse("2026-11-01T07:00:00Z"),Instant.parse("2026-11-01T07:30:00Z"));
    }

    private AvailabilityResult calculate(List<WorkingScheduleRule> rules,List<ScheduleBreak> breaks,List<ScheduleException> exceptions,List<TimeInterval> busy,int duration,int before,int after){return engine("2026-08-17T00:00:00Z").calculate(input(MONDAY,UTC,rules,breaks,exceptions,busy,duration,before,after,0,30,15));}
    private AvailabilityEngine engine(String instant){return new AvailabilityEngine(Clock.fixed(Instant.parse(instant),UTC));}
    private AvailabilityInput input(LocalDate date,ZoneId zone,List<WorkingScheduleRule> rules,List<ScheduleBreak> breaks,List<ScheduleException> exceptions,List<TimeInterval> busy,int duration,int before,int after,int lead,int horizon,int step){return new AvailabilityInput(BRANCH,EMPLOYEE,date,zone,rules,breaks,exceptions,busy,Duration.ofMinutes(duration),Duration.ofMinutes(before),Duration.ofMinutes(after),Duration.ofMinutes(lead),horizon,Duration.ofMinutes(step));}
    private WorkingScheduleRule rule(UUID branch,LocalDate date,String start,String end){return new WorkingScheduleRule(UUID.randomUUID(),BUSINESS,branch,EMPLOYEE,date.getDayOfWeek(),LocalTime.parse(start),LocalTime.parse(end),date.minusDays(30),date.plusDays(30),CREATED,CREATED);}
    private ScheduleBreak scheduleBreak(WorkingScheduleRule rule,String start,String end){return new ScheduleBreak(UUID.randomUUID(),BUSINESS,rule.id(),LocalTime.parse(start),LocalTime.parse(end),CREATED,CREATED);}
    private ScheduleException exception(UUID branch,LocalDate date,ScheduleExceptionType type,String start,String end){return new ScheduleException(UUID.randomUUID(),BUSINESS,branch,EMPLOYEE,date,type,start==null?null:LocalTime.parse(start),end==null?null:LocalTime.parse(end),null,CREATED,CREATED);}
    private List<LocalTime> times(AvailabilityResult result){return result.slots().stream().map(value->value.start().atZone(result.zoneId()).toLocalTime()).toList();}
    private TimeInterval at(String start,String end){return new TimeInterval(MONDAY.atTime(LocalTime.parse(start)).toInstant(ZoneOffset.UTC),MONDAY.atTime(LocalTime.parse(end)).toInstant(ZoneOffset.UTC));}
    private TimeInterval occupied(AvailabilitySlot slot,int before,int after){return new TimeInterval(slot.start().minus(Duration.ofMinutes(before)),slot.end().plus(Duration.ofMinutes(after)));}
}
