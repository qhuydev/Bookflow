package com.bookflow.schedules.availability;

import java.time.Instant;
import java.util.*;

/** Immutable half-open interval [start, end). */
public record TimeInterval(Instant start, Instant end) {
    public TimeInterval {
        Objects.requireNonNull(start,"start"); Objects.requireNonNull(end,"end");
        if(!start.isBefore(end)) throw new IllegalArgumentException("Interval start must be before end.");
    }
    public boolean overlaps(TimeInterval other){return start.isBefore(other.end)&&end.isAfter(other.start);}
    public boolean contains(TimeInterval other){return !other.start.isBefore(start)&&!other.end.isAfter(end);}
    public Optional<TimeInterval> intersection(TimeInterval other){
        Instant s=start.isAfter(other.start)?start:other.start,e=end.isBefore(other.end)?end:other.end;
        return s.isBefore(e)?Optional.of(new TimeInterval(s,e)):Optional.empty();
    }
    public List<TimeInterval> subtract(TimeInterval blocker){
        if(!overlaps(blocker))return List.of(this);
        List<TimeInterval> result=new ArrayList<>(2);
        if(start.isBefore(blocker.start))result.add(new TimeInterval(start,blocker.start));
        if(end.isAfter(blocker.end))result.add(new TimeInterval(blocker.end,end));
        return List.copyOf(result);
    }
    public static List<TimeInterval> merge(Collection<TimeInterval> source){
        if(source.isEmpty())return List.of();
        List<TimeInterval> sorted=source.stream().sorted(Comparator.comparing(TimeInterval::start).thenComparing(TimeInterval::end)).toList();
        List<TimeInterval> result=new ArrayList<>();TimeInterval current=sorted.getFirst();
        for(int i=1;i<sorted.size();i++){TimeInterval next=sorted.get(i);if(!next.start.isAfter(current.end)){current=new TimeInterval(current.start,current.end.isAfter(next.end)?current.end:next.end);}else{result.add(current);current=next;}}
        result.add(current);return List.copyOf(result);
    }
    public static List<TimeInterval> subtractMany(Collection<TimeInterval> source,Collection<TimeInterval> blockers){
        List<TimeInterval> result=merge(source);
        for(TimeInterval blocker:merge(blockers)){result=result.stream().flatMap(value->value.subtract(blocker).stream()).toList();if(result.isEmpty())break;}
        return List.copyOf(result);
    }
}
