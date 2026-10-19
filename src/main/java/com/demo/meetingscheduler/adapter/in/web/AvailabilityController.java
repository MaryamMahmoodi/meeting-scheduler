package com.demo.meetingscheduler.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.QueryAvailabilityUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/availability")
public class AvailabilityController {

	private final QueryAvailabilityUseCase availability;

	public AvailabilityController(QueryAvailabilityUseCase availability) {
		this.availability = availability;
	}

	@GetMapping
	public AvailabilityResponse getAvailability(@PathVariable UUID userId, @RequestParam Instant from,
			@RequestParam Instant to) {
		return AvailabilityResponse.from(availability.get(userId, from, to));
	}

}
