package com.demo.meetingscheduler.domain.model;

import java.time.Instant;

import com.demo.meetingscheduler.domain.exception.DomainException;

/** The half-open range {@code [from, to)} of a list or availability query. */
public record TimeRange(Instant from, Instant to) {

	public static TimeRange of(Instant from, Instant to) {
		Instant start = Timestamps.requireSupported("from", from);
		Instant end = Timestamps.requireSupported("to", to);
		if (!start.isBefore(end)) {
			throw DomainException.validation("to", "must be after from");
		}
		return new TimeRange(start, end);
	}

}
