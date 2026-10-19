package com.demo.meetingscheduler.adapter.in.web;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.Meeting;

public record MeetingResponse(
		UUID id,
		String title,
		String description,
		UUID organizerId,
		UUID slotId,
		Instant startTime,
		Instant endTime,
		List<UUID> participantIds,
		Instant createdAt) {

	static MeetingResponse from(Meeting meeting) {
		// Sorted by the canonical lower-case string so the order is the same everywhere.
		List<UUID> participants = meeting.participantIds().stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		return new MeetingResponse(meeting.id(), meeting.title(), meeting.description(), meeting.organizerId(),
				meeting.slotId(), meeting.startTime(), meeting.endTime(), participants, meeting.createdAt());
	}

}
