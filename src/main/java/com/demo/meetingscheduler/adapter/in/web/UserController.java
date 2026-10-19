package com.demo.meetingscheduler.adapter.in.web;

import java.net.URI;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageUsersUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final ManageUsersUseCase users;

	public UserController(ManageUsersUseCase users) {
		this.users = users;
	}

	@PostMapping
	public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
		UserResponse user = UserResponse.from(users.register(request.toCommand()));
		URI location = UriComponentsBuilder.fromPath("/api/v1/users/{userId}").build(user.id());
		return ResponseEntity.created(location).body(user);
	}

	@GetMapping("/{userId}")
	public UserResponse getUser(@PathVariable UUID userId) {
		return UserResponse.from(users.get(userId));
	}

}
