package com.example.meetingscheduler.domain;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
class TimeRangeTest {
  @Test void rejectsInvalidRange(){var s=OffsetDateTime.parse("2026-10-17T10:00:00+02:00");var e=OffsetDateTime.parse("2026-10-17T09:00:00+02:00");assertThrows(IllegalArgumentException.class,()->new TimeRange(s,e));}
  @Test void detectsOverlap(){var a=new TimeRange(OffsetDateTime.parse("2026-10-17T10:00:00+02:00"),OffsetDateTime.parse("2026-10-17T11:00:00+02:00"));var b=new TimeRange(OffsetDateTime.parse("2026-10-17T10:30:00+02:00"),OffsetDateTime.parse("2026-10-17T11:30:00+02:00"));assertTrue(a.overlaps(b));}
}
