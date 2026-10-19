package com.demo.meetingscheduler.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.demo.meetingscheduler.domain.exception.DomainException;

/** Timestamps have microsecond precision (the precision of the storage and of the contract); everything is truncated to match. */
public final class Timestamps {

	/** Earliest supported timestamp (inclusive). */
	public static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
	/** Latest supported timestamp (inclusive). */
	public static final Instant MAX = Instant.parse("9999-12-31T23:59:59.999999Z");

	private Timestamps() {
	}

	public static Instant truncate(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MICROS);
	}

	public static Instant now() {
		return truncate(Instant.now());
	}

	/** Rejects timestamps outside {@code [MIN, MAX]} (validation), and returns the value truncated to micros otherwise. */
	public static Instant requireSupported(String field, Instant instant) {
		if (instant.isBefore(MIN) || instant.isAfter(MAX)) {
			throw DomainException.validation(field, "must be between " + MIN + " and " + MAX);
		}
		return truncate(instant);
	}

}
