package com.demo.meetingscheduler.domain.model;

import java.time.Instant;

import com.demo.meetingscheduler.domain.exception.DomainException;

/** The validated start and end of a time slot: supported, truncated to microseconds, end after start. */
public record SlotInterval(Instant start, Instant end) {

	public static SlotInterval of(Instant startTime, Instant endTime) {
		Instant start = Timestamps.requireSupported("startTime", startTime);
		Instant end = Timestamps.requireSupported("endTime", endTime);
		if (!end.isAfter(start)) {
			throw DomainException.validation("endTime", "must be after startTime");
		}
		return new SlotInterval(start, end);
	}

}
