package com.example.meetingscheduler.domain;
import java.time.OffsetDateTime;
public record TimeRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
  public TimeRange {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
      throw new IllegalArgumentException("endsAt must be after startsAt");
  }
  public boolean overlaps(TimeRange other) {
    return startsAt.isBefore(other.endsAt()) && other.startsAt().isBefore(endsAt);
  }
}
