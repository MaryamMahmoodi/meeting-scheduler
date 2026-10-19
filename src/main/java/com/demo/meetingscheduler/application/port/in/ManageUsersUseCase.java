package com.demo.meetingscheduler.application.port.in;

import java.util.UUID;

import com.demo.meetingscheduler.domain.model.User;

public interface ManageUsersUseCase {

	/** Registers a user. Fails with a conflict if the email is already registered (case-insensitive). */
	User register(RegisterUserCommand command);

	User get(UUID userId);

	record RegisterUserCommand(String name, String email) {
	}

}
