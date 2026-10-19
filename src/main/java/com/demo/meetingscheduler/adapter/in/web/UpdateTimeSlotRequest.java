package com.demo.meetingscheduler.adapter.in.web;

import java.time.Instant;

import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase.UpdateTimeSlotCommand;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTimeSlotRequest(
		@NotNull Instant startTime,
		@NotNull Instant endTime,
		TimeSlotStatus status) {

	UpdateTimeSlotCommand toCommand() {
		return new UpdateTimeSlotCommand(startTime, endTime, status);
	}

}
