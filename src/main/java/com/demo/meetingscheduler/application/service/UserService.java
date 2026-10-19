package com.demo.meetingscheduler.application.service;

import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageUsersUseCase;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

public class UserService implements ManageUsersUseCase {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional
	public User register(RegisterUserCommand command) {
		User user = User.register(command.name(), command.email());
		if (users.existsByEmail(user.email())) {
			throw DomainException.conflict(ErrorCode.EMAIL_ALREADY_REGISTERED,
					"A user with email '" + user.email() + "' already exists.");
		}
		// The repository writes through, so a concurrent duplicate surfaces as a conflict right here.
		return users.save(user);
	}

	@Override
	@Transactional(readOnly = true)
	public User get(UUID userId) {
		return users.findById(userId).orElseThrow(() -> Lookups.userNotFound(userId));
	}

}
