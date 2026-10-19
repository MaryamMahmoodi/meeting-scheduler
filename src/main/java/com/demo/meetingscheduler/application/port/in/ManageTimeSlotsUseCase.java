package com.demo.meetingscheduler.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;

/**
 * Time slots of a user's personal calendar. A slot is always addressed through its owner, so another
 * user's slot is reported as not found.
 */
public interface ManageTimeSlotsUseCase {

	TimeSlot create(UUID userId, CreateTimeSlotCommand command);

	TimeSlot get(UUID userId, UUID slotId);

	/** The user's slots that intersect {@code [from, to)}, whole and ordered by start. */
	List<TimeSlot> list(UUID userId, Instant from, Instant to);

	TimeSlot update(UUID userId, UUID slotId, UpdateTimeSlotCommand command);

	TimeSlot updateAvailability(UUID userId, UUID slotId, TimeSlotStatus status);

	void delete(UUID userId, UUID slotId);

	/** A missing status means {@link TimeSlotStatus#FREE}. */
	record CreateTimeSlotCommand(Instant startTime, Instant endTime, TimeSlotStatus status) {
	}

	/** A missing status leaves the current status unchanged. */
	record UpdateTimeSlotCommand(Instant startTime, Instant endTime, TimeSlotStatus status) {
	}

}
