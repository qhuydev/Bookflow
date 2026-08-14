package com.bookflow.schedules.application;

import com.bookflow.schedules.api.*;
import com.bookflow.schedules.domain.*;
import com.bookflow.shared.error.*;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

@Component
public class ScheduleRequestValidator {
    public RuleValues createRule(WorkingRuleRequest request) {
        if (request == null) throw requiredBody();
        return rule(request.branchId(), request.weekday(), request.startLocalTime(), request.endLocalTime(),
                request.effectiveFrom(), request.effectiveTo());
    }

    public RuleValues updateRule(WorkingRuleRequest request, WorkingScheduleRule current) {
        if (request == null) throw requiredBody();
        if (request.branchId() == null && request.weekday() == null && request.startLocalTime() == null
                && request.endLocalTime() == null && request.effectiveFrom() == null && request.effectiveTo() == null) {
            throw invalid("$", "NotEmpty", "At least one schedule rule field is required.");
        }
        return rule(or(request.branchId(), current.branchId()), or(request.weekday(), current.weekday()),
                or(request.startLocalTime(), current.startLocalTime()), or(request.endLocalTime(), current.endLocalTime()),
                or(request.effectiveFrom(), current.effectiveFrom()), or(request.effectiveTo(), current.effectiveTo()));
    }

    public BreakValues createBreak(ScheduleBreakRequest request, WorkingScheduleRule rule) {
        if (request == null) throw requiredBody();
        return scheduleBreak(request.startLocalTime(), request.endLocalTime(), rule);
    }

    public BreakValues updateBreak(ScheduleBreakRequest request, ScheduleBreak current, WorkingScheduleRule rule) {
        if (request == null) throw requiredBody();
        if (request.startLocalTime() == null && request.endLocalTime() == null) {
            throw invalid("$", "NotEmpty", "At least one break field is required.");
        }
        return scheduleBreak(or(request.startLocalTime(), current.startLocalTime()),
                or(request.endLocalTime(), current.endLocalTime()), rule);
    }

    public void requireBreaksWithinRule(RuleValues rule, List<ScheduleBreak> breaks) {
        if (breaks.stream().anyMatch(value -> value.startLocalTime().isBefore(rule.startLocalTime())
                || value.endLocalTime().isAfter(rule.endLocalTime()))) {
            throw invalid("$", "Range", "Existing breaks must remain fully contained in the working rule.");
        }
    }

    public ExceptionValues createException(ScheduleExceptionRequest request) {
        if (request == null) throw requiredBody();
        return exception(request.branchId(), request.date(), request.type(), request.startLocalTime(),
                request.endLocalTime(), request.note());
    }

    public ExceptionValues updateException(ScheduleExceptionRequest request, ScheduleException current) {
        if (request == null) throw requiredBody();
        if (request.branchId() == null && request.date() == null && request.type() == null
                && request.startLocalTime() == null && request.endLocalTime() == null && request.note() == null) {
            throw invalid("$", "NotEmpty", "At least one exception field is required.");
        }
        return exception(or(request.branchId(), current.branchId()), or(request.date(), current.date()),
                or(request.type(), current.type()), or(request.startLocalTime(), current.startLocalTime()),
                or(request.endLocalTime(), current.endLocalTime()), request.note() == null ? current.note() : request.note());
    }

    private RuleValues rule(UUID branch, DayOfWeek weekday, LocalTime start, LocalTime end,
                            LocalDate from, LocalDate to) {
        List<ApiFieldViolation> errors = new ArrayList<>();
        required(branch, "branchId", errors); required(weekday, "weekday", errors);
        required(start, "startLocalTime", errors); required(end, "endLocalTime", errors);
        required(from, "effectiveFrom", errors);
        if (start != null && end != null && !start.isBefore(end))
            errors.add(v("endLocalTime", "Range", "End time must be after start time."));
        if (from != null && to != null && from.isAfter(to))
            errors.add(v("effectiveTo", "Range", "Effective end date must not be before start date."));
        fail(errors); return new RuleValues(branch, weekday, start, end, from, to);
    }

    private BreakValues scheduleBreak(LocalTime start, LocalTime end, WorkingScheduleRule rule) {
        List<ApiFieldViolation> errors = new ArrayList<>();
        required(start, "startLocalTime", errors); required(end, "endLocalTime", errors);
        if (start != null && end != null && !start.isBefore(end))
            errors.add(v("endLocalTime", "Range", "End time must be after start time."));
        if (start != null && end != null && (start.isBefore(rule.startLocalTime()) || end.isAfter(rule.endLocalTime())))
            errors.add(v("$", "Range", "Break must be fully contained in the working rule."));
        fail(errors); return new BreakValues(start, end);
    }

    private ExceptionValues exception(UUID branch, LocalDate date, ScheduleExceptionType type,
                                      LocalTime start, LocalTime end, String rawNote) {
        List<ApiFieldViolation> errors = new ArrayList<>();
        required(branch, "branchId", errors); required(date, "date", errors); required(type, "type", errors);
        boolean bothNull = start == null && end == null;
        boolean bothPresent = start != null && end != null;
        if (!bothNull && !bothPresent) errors.add(v("$", "Range", "Start and end time must both be supplied or both omitted."));
        if (bothPresent && !start.isBefore(end)) errors.add(v("endLocalTime", "Range", "End time must be after start time."));
        if (type == ScheduleExceptionType.WORKING_OVERRIDE && !bothPresent)
            errors.add(v("$", "Required", "Working override requires start and end time."));
        String note = rawNote == null ? null : rawNote.strip();
        if (note != null && (note.isEmpty() || note.length() > 500))
            errors.add(v("note", "Size", "Note must contain between 1 and 500 characters."));
        fail(errors); return new ExceptionValues(branch, date, type, start, end, note);
    }

    private void required(Object value, String field, List<ApiFieldViolation> errors) {
        if (value == null) errors.add(v(field, "NotNull", "Value is required."));
    }
    private ApiFieldViolation v(String field, String code, String message) { return new ApiFieldViolation(field, code, message); }
    private void fail(List<ApiFieldViolation> errors) { if (!errors.isEmpty()) throw new RequestValidationException(errors); }
    private RequestValidationException invalid(String field, String code, String message) { return new RequestValidationException(List.of(v(field, code, message))); }
    private RequestValidationException requiredBody() { return invalid("$", "NotNull", "Request body is required."); }
    private <T> T or(T value, T fallback) { return value == null ? fallback : value; }

    public record RuleValues(UUID branchId, DayOfWeek weekday, LocalTime startLocalTime, LocalTime endLocalTime,
                             LocalDate effectiveFrom, LocalDate effectiveTo) { }
    public record BreakValues(LocalTime startLocalTime, LocalTime endLocalTime) { }
    public record ExceptionValues(UUID branchId, LocalDate date, ScheduleExceptionType type,
                                  LocalTime startLocalTime, LocalTime endLocalTime, String note) { }
}
