package com.demo.meetingscheduler.adapter.in.web;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.QueryAvailabilityUseCase.Availability;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;

/** Free/busy view. Time not covered by a slot is unknown, not free. */
public record AvailabilityResponse(UUID userId, Instant from, Instant to, List<AvailabilitySlot> slots) {

	static AvailabilityResponse from(Availability availability) {
		List<AvailabilitySlot> slots = availability.slots().stream()
				.map(slot -> new AvailabilitySlot(slot.startTime(), slot.endTime(), slot.status())).toList();
		return new AvailabilityResponse(availability.userId(), availability.range().from(),
				availability.range().to(), slots);
	}

	public record AvailabilitySlot(Instant startTime, Instant endTime, TimeSlotStatus status) {
	}

}
