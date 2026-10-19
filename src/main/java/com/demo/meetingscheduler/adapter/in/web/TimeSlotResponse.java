package com.demo.meetingscheduler.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;

public record TimeSlotResponse(
		UUID id,
		UUID userId,
		Instant startTime,
		Instant endTime,
		TimeSlotStatus status,
		Instant createdAt) {

	static TimeSlotResponse from(TimeSlot slot) {
		return new TimeSlotResponse(slot.id(), slot.userId(), slot.startTime(), slot.endTime(), slot.status(),
				slot.createdAt());
	}

}
