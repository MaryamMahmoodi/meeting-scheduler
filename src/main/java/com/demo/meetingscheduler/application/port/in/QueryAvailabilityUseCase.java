package com.demo.meetingscheduler.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;

public interface QueryAvailabilityUseCase {

	/**
	 * The user's slots that intersect {@code [from, to)}, whole and unmerged. Gaps between slots are
	 * intentionally not reported: they mean unknown availability.
	 */
	Availability get(UUID userId, Instant from, Instant to);

	record Availability(UUID userId, TimeRange range, List<TimeSlot> slots) {
	}

}
