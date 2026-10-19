package com.demo.meetingscheduler.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageMeetingsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users/{userId}/meetings")
public class MeetingController {

	private final ManageMeetingsUseCase meetings;

	public MeetingController(ManageMeetingsUseCase meetings) {
		this.meetings = meetings;
	}

	@PostMapping
	public ResponseEntity<MeetingResponse> createMeeting(@PathVariable UUID userId,
			@Valid @RequestBody CreateMeetingRequest request) {
		MeetingResponse meeting = MeetingResponse.from(meetings.create(userId, request.toCommand()));
		URI location = UriComponentsBuilder.fromPath("/api/v1/users/{userId}/meetings/{meetingId}")
				.build(userId, meeting.id());
		return ResponseEntity.created(location).body(meeting);
	}

	@GetMapping
	public List<MeetingResponse> listMeetings(@PathVariable UUID userId, @RequestParam Instant from,
			@RequestParam Instant to) {
		return meetings.list(userId, from, to).stream().map(MeetingResponse::from).toList();
	}

	@GetMapping("/{meetingId}")
	public MeetingResponse getMeeting(@PathVariable UUID userId, @PathVariable UUID meetingId) {
		return MeetingResponse.from(meetings.get(userId, meetingId));
	}

}
