package com.demo.meetingscheduler.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users/{userId}/slots")
public class TimeSlotController {

	private final ManageTimeSlotsUseCase timeSlots;

	public TimeSlotController(ManageTimeSlotsUseCase timeSlots) {
		this.timeSlots = timeSlots;
	}

	@PostMapping
	public ResponseEntity<TimeSlotResponse> createSlot(@PathVariable UUID userId,
			@Valid @RequestBody CreateTimeSlotRequest request) {
		TimeSlotResponse slot = TimeSlotResponse.from(timeSlots.create(userId, request.toCommand()));
		URI location = UriComponentsBuilder.fromPath("/api/v1/users/{userId}/slots/{slotId}").build(userId, slot.id());
		return ResponseEntity.created(location).body(slot);
	}

	@GetMapping
	public List<TimeSlotResponse> listSlots(@PathVariable UUID userId, @RequestParam Instant from,
			@RequestParam Instant to) {
		return timeSlots.list(userId, from, to).stream().map(TimeSlotResponse::from).toList();
	}

	@GetMapping("/{slotId}")
	public TimeSlotResponse getSlot(@PathVariable UUID userId, @PathVariable UUID slotId) {
		return TimeSlotResponse.from(timeSlots.get(userId, slotId));
	}

	@PutMapping("/{slotId}")
	public TimeSlotResponse updateSlot(@PathVariable UUID userId, @PathVariable UUID slotId,
			@Valid @RequestBody UpdateTimeSlotRequest request) {
		return TimeSlotResponse.from(timeSlots.update(userId, slotId, request.toCommand()));
	}

	@PatchMapping("/{slotId}/availability")
	public TimeSlotResponse updateSlotAvailability(@PathVariable UUID userId, @PathVariable UUID slotId,
			@Valid @RequestBody UpdateAvailabilityRequest request) {
		return TimeSlotResponse.from(timeSlots.updateAvailability(userId, slotId, request.status()));
	}

	@DeleteMapping("/{slotId}")
	public ResponseEntity<Void> deleteSlot(@PathVariable UUID userId, @PathVariable UUID slotId) {
		timeSlots.delete(userId, slotId);
		return ResponseEntity.noContent().build();
	}

}
