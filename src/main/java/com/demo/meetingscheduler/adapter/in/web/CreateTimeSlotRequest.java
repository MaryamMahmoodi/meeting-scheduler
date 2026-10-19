package com.demo.meetingscheduler.adapter.in.web;

import java.time.Instant;

import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase.CreateTimeSlotCommand;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;
import jakarta.validation.constraints.NotNull;

public record CreateTimeSlotRequest(
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		TimeSlotStatus status) {

	CreateTimeSlotCommand toCommand() {
		return new CreateTimeSlotCommand(startTime, endTime, status);
	}

}
