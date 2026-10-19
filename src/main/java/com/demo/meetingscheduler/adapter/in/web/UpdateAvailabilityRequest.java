package com.demo.meetingscheduler.adapter.in.web;

import com.demo.meetingscheduler.domain.model.TimeSlotStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(@NotNull TimeSlotStatus status) {
}
