package com.bookflow.schedules.application;

import com.bookflow.schedules.api.*;
import com.bookflow.schedules.domain.*;
import com.bookflow.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ScheduleRequestValidatorTest {
    private final ScheduleRequestValidator validator = new ScheduleRequestValidator();
    private final UUID branch = UUID.randomUUID();

    @Test void acceptsHalfOpenRuleAndRejectsInvalidRanges(){
        assertThat(validator.createRule(new WorkingRuleRequest(branch,DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(12,0),LocalDate.of(2026,8,1),null)).endLocalTime()).isEqualTo(LocalTime.NOON);
        assertThatThrownBy(()->validator.createRule(new WorkingRuleRequest(branch,DayOfWeek.MONDAY,LocalTime.NOON,LocalTime.NOON,LocalDate.now(),null))).isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(()->validator.createRule(new WorkingRuleRequest(branch,DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.NOON,LocalDate.of(2026,9,1),LocalDate.of(2026,8,1)))).isInstanceOf(RequestValidationException.class);
    }
    @Test void validatesBreakContainmentAndExceptionSemantics(){
        var rule=new WorkingScheduleRule(UUID.randomUUID(),UUID.randomUUID(),branch,UUID.randomUUID(),DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(18,0),LocalDate.now(),null,Instant.now(),Instant.now());
        assertThat(validator.createBreak(new ScheduleBreakRequest(LocalTime.NOON,LocalTime.of(13,0)),rule).startLocalTime()).isEqualTo(LocalTime.NOON);
        assertThatThrownBy(()->validator.createBreak(new ScheduleBreakRequest(LocalTime.of(8,0),LocalTime.of(10,0)),rule)).isInstanceOf(RequestValidationException.class);
        assertThat(validator.createException(new ScheduleExceptionRequest(branch,LocalDate.now(),ScheduleExceptionType.TIME_OFF,null,null," Nghỉ ")).note()).isEqualTo("Nghỉ");
        assertThatThrownBy(()->validator.createException(new ScheduleExceptionRequest(branch,LocalDate.now(),ScheduleExceptionType.WORKING_OVERRIDE,null,null,null))).isInstanceOf(RequestValidationException.class);
    }
}
