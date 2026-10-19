package com.demo.meetingscheduler.adapter.in.web;

import com.demo.meetingscheduler.application.port.in.ManageUsersUseCase.RegisterUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Size(max = 100) @NoNul String name,
		@NotBlank @Email @Size(max = 254) @NoNul String email) {

	RegisterUserCommand toCommand() {
		return new RegisterUserCommand(name, email);
	}

}
