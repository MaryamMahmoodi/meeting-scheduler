package com.demo.meetingscheduler.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.Meeting;

public interface ManageMeetingsUseCase {

	/**
	 * Books a meeting from one of the organizer's free slots. Only the organizer's slot is reserved;
	 * participants are stored as user IDs and their availability is neither checked nor reserved.
	 */
	Meeting create(UUID organizerId, CreateMeetingCommand command);

	/** The meetings the user organizes or participates in that intersect {@code [from, to)}. */
	List<Meeting> list(UUID userId, Instant from, Instant to);

	Meeting get(UUID userId, UUID meetingId);

	/** {@code participantIds} may be {@code null}, which means no participants. */
	record CreateMeetingCommand(UUID slotId, String title, String description, List<UUID> participantIds) {
	}

}
