package com.bookflow.schedules.availability;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TimeIntervalTest {
    private TimeInterval interval(String start,String end){return new TimeInterval(Instant.parse("2026-08-17T"+start+":00Z"),Instant.parse("2026-08-17T"+end+":00Z"));}
    @Test void halfOpenBoundariesDoNotOverlap(){assertThat(interval("09:00","10:00").overlaps(interval("10:00","11:00"))).isFalse();}
    @Test void subtractAndMergeAreDeterministic(){
        assertThat(interval("09:00","18:00").subtract(interval("12:00","13:00"))).containsExactly(interval("09:00","12:00"),interval("13:00","18:00"));
        assertThat(TimeInterval.merge(List.of(interval("13:00","18:00"),interval("09:00","12:00"),interval("12:00","13:00")))).containsExactly(interval("09:00","18:00"));
    }
}
