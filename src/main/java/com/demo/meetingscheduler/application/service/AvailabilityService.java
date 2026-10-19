package com.demo.meetingscheduler.application.service;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.QueryAvailabilityUseCase;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.model.TimeRange;
import org.springframework.transaction.annotation.Transactional;

public class AvailabilityService implements QueryAvailabilityUseCase {

	private final TimeSlotRepository slots;
	private final UserRepository users;

	public AvailabilityService(TimeSlotRepository slots, UserRepository users) {
		this.slots = slots;
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public Availability get(UUID userId, Instant from, Instant to) {
		TimeRange range = TimeRange.of(from, to);
		Lookups.requireUser(users, userId);
		return new Availability(userId, range, slots.findIntersecting(userId, range));
	}

}
