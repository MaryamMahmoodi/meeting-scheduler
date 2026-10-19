package com.demo.meetingscheduler.adapter.in.web;

import java.util.UUID;

import com.demo.meetingscheduler.domain.model.User;

public record UserResponse(UUID id, String name, String email) {

	static UserResponse from(User user) {
		return new UserResponse(user.id(), user.name(), user.email());
	}

}
