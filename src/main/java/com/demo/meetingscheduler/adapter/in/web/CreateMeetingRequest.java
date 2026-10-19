package com.demo.meetingscheduler.adapter.in.web;

import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageMeetingsUseCase.CreateMeetingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMeetingRequest(
		@NotNull UUID slotId,
		@NotBlank @Size(max = 200) @NoNul String title,
		@Size(max = 2000) @NoNul String description,
		// Omitted and null both mean "no participants".
		@Size(max = 100) List<@NotNull UUID> participantIds) {

	CreateMeetingCommand toCommand() {
		return new CreateMeetingCommand(slotId, title, description, participantIds);
	}

}
