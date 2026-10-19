package com.demo.meetingscheduler.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A meeting booked from the organizer's slot. Participants are plain user IDs. {@code id} is {@code null}
 * until the meeting has been saved.
 */
public record Meeting(
		UUID id,
		UUID organizerId,
		UUID slotId,
		String title,
		String description,
		Instant startTime,
		Instant endTime,
		Set<UUID> participantIds,
		Instant createdAt) {

	public Meeting {
		participantIds = Set.copyOf(participantIds);
	}

	/** A new, not yet saved meeting. It takes its start and end from the (already reserved) slot. */
	public static Meeting book(UUID organizerId, TimeSlot slot, String title, String description,
			Set<UUID> participantIds) {
		return new Meeting(null, organizerId, slot.id(), title, description, slot.startTime(), slot.endTime(),
				participantIds, Timestamps.now());
	}

}
