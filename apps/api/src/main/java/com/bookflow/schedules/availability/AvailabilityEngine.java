package com.bookflow.schedules.availability;

import com.bookflow.schedules.domain.*;
import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.util.*;

/** Pure, deterministic one-day availability calculation. It never loads external state. */
public final class AvailabilityEngine {
    private final Clock clock;
    public AvailabilityEngine(Clock clock){this.clock=Objects.requireNonNull(clock);}

    public AvailabilityResult calculate(AvailabilityInput input){
        Objects.requireNonNull(input);
        LocalDate today=LocalDate.now(clock.withZone(input.zoneId()));
        if(input.date().isBefore(today)||input.date().isAfter(today.plusDays(input.maxBookingAdvanceDays())))return empty(input);

        List<ScheduleException> exceptions=input.exceptions().stream().filter(value->value.branchId().equals(input.branchId())
                &&value.employeeId().equals(input.employeeId())&&value.date().equals(input.date())).toList();
        if(exceptions.stream().anyMatch(value->value.type()==ScheduleExceptionType.TIME_OFF&&value.startLocalTime()==null))return empty(input);

        List<TimeInterval> working=new ArrayList<>();
        for(WorkingScheduleRule rule:input.workingRules()){
            if(!applies(rule,input))continue;
            TimeInterval interval=toInterval(input.date(),rule.startLocalTime(),rule.endLocalTime(),input.zoneId());
            List<TimeInterval> ruleBreaks=input.breaks().stream().filter(value->value.scheduleRuleId().equals(rule.id()))
                    .map(value->toInterval(input.date(),value.startLocalTime(),value.endLocalTime(),input.zoneId())).toList();
            working.addAll(TimeInterval.subtractMany(List.of(interval),ruleBreaks));
        }
        exceptions.stream().filter(value->value.type()==ScheduleExceptionType.WORKING_OVERRIDE)
                .map(value->toInterval(input.date(),value.startLocalTime(),value.endLocalTime(),input.zoneId())).forEach(working::add);
        List<TimeInterval> timeOff=exceptions.stream().filter(value->value.type()==ScheduleExceptionType.TIME_OFF)
                .map(value->toInterval(input.date(),value.startLocalTime(),value.endLocalTime(),input.zoneId())).toList();
        List<TimeInterval> free=TimeInterval.subtractMany(TimeInterval.subtractMany(working,timeOff),input.busyIntervals());
        if(free.isEmpty())return empty(input);

        Instant earliest=clock.instant().plus(input.leadTime());
        List<AvailabilitySlot> slots=new ArrayList<>();Set<Instant> seen=new HashSet<>();
        LocalDateTime candidate=input.date().atStartOfDay(),nextDay=input.date().plusDays(1).atStartOfDay();
        while(candidate.isBefore(nextDay)){
            Instant start=resolve(candidate,input.zoneId());
            if(seen.add(start)&&!start.isBefore(earliest)){
                Instant customerEnd=start.plus(input.serviceDuration());
                TimeInterval occupied=new TimeInterval(start.minus(input.bufferBefore()),customerEnd.plus(input.bufferAfter()));
                if(free.stream().anyMatch(value->value.contains(occupied)))slots.add(new AvailabilitySlot(start,customerEnd));
            }
            candidate=candidate.plus(input.slotStep());
        }
        slots.sort(Comparator.comparing(AvailabilitySlot::start));return new AvailabilityResult(input.date(),input.zoneId(),slots);
    }

    private boolean applies(WorkingScheduleRule rule,AvailabilityInput input){
        return rule.branchId().equals(input.branchId())&&rule.employeeId().equals(input.employeeId())
                &&rule.weekday()==input.date().getDayOfWeek()&&!input.date().isBefore(rule.effectiveFrom())
                &&(rule.effectiveTo()==null||!input.date().isAfter(rule.effectiveTo()));
    }
    private TimeInterval toInterval(LocalDate date,LocalTime start,LocalTime end,ZoneId zone){return new TimeInterval(resolve(date.atTime(start),zone),resolve(date.atTime(end),zone));}
    private Instant resolve(LocalDateTime local,ZoneId zone){
        var rules=zone.getRules();var offsets=rules.getValidOffsets(local);
        if(offsets.size()==1)return local.toInstant(offsets.getFirst());
        if(offsets.size()==2)return local.toInstant(offsets.getFirst()); // deterministic earlier offset, no duplicate ambiguous slot
        ZoneOffsetTransition transition=rules.getTransition(local);
        return transition.getDateTimeAfter().toInstant(transition.getOffsetAfter()); // shift DST-gap local time forward
    }
    private AvailabilityResult empty(AvailabilityInput input){return new AvailabilityResult(input.date(),input.zoneId(),List.of());}
}
