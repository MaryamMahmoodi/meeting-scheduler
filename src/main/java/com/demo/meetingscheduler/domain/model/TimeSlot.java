package com.demo.meetingscheduler.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

/**
 * A slot of a user's personal calendar (the calendar itself is not modelled). Immutable: every change
 * returns a new instance. {@code id} is {@code null} until the slot has been saved.
 */
public record TimeSlot(
		UUID id,
		UUID userId,
		Instant startTime,
		Instant endTime,
		TimeSlotStatus status,
		Instant createdAt) {

	/** A new, not yet saved slot. A missing status means {@link TimeSlotStatus#FREE}. */
	public static TimeSlot create(UUID userId, SlotInterval interval, TimeSlotStatus status) {
		return new TimeSlot(null, userId, interval.start(), interval.end(),
				status != null ? status : TimeSlotStatus.FREE, Timestamps.now());
	}

	public TimeSlot reschedule(SlotInterval interval) {
		return new TimeSlot(id, userId, interval.start(), interval.end(), status, createdAt);
	}

	public TimeSlot withStatus(TimeSlotStatus newStatus) {
		return new TimeSlot(id, userId, startTime, endTime, newStatus, createdAt);
	}

	/** Reserves the slot for a meeting. Only a free slot can be booked. */
	public TimeSlot book() {
		if (status != TimeSlotStatus.FREE) {
			throw DomainException.conflict(ErrorCode.SLOT_NOT_FREE,
					"Time slot " + id + " is " + status + " and cannot be booked.");
		}
		return withStatus(TimeSlotStatus.BUSY);
	}

}
