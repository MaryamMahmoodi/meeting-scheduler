package com.demo.meetingscheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.meetingscheduler.application.port.in.ManageUsersUseCase.RegisterUserCommand;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.User;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	private UserService service() {
		return new UserService(userRepository);
	}

	@Test
	void registerLowerCasesTheEmailBeforeCheckingAndSaving() {
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User response = service().register(new RegisterUserCommand("Alice", "Alice@EXAMPLE.Com"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().email()).isEqualTo("alice@example.com");
		assertThat(saved.getValue().name()).isEqualTo("Alice");
		assertThat(response.email()).isEqualTo("alice@example.com");
		assertThat(response.name()).isEqualTo("Alice");
	}

	@Test
	void registerReturnsTheSavedUser() {
		User stored = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.parse("2030-01-01T09:00:00Z"));
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenReturn(stored);

		User response = service().register(new RegisterUserCommand("Alice", "alice@example.com"));

		assertThat(response).isSameAs(stored);
	}

	@Test
	void registerWithDuplicateEmailIsConflictAndNothingIsSaved() {
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service().register(new RegisterUserCommand("Alice", "ALICE@example.com")))
				.isInstanceOf(DomainException.class).satisfies(ex -> {
					DomainException domain = (DomainException) ex;
					assertThat(domain.kind()).isEqualTo(DomainException.Kind.CONFLICT);
					assertThat(domain.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
				});

		verify(userRepository, never()).save(any());
	}

	@Test
	void getReturnsTheUser() {
		UUID id = UUID.randomUUID();
		User user = new User(id, "Bob", "bob@example.com", Instant.parse("2030-01-01T09:00:00Z"));
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		User response = service().get(id);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.name()).isEqualTo("Bob");
		assertThat(response.email()).isEqualTo("bob@example.com");
	}

	@Test
	void getUnknownUserIsNotFound() {
		UUID id = UUID.randomUUID();
		when(userRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().get(id)).isInstanceOf(DomainException.class).satisfies(ex -> {
			DomainException domain = (DomainException) ex;
			assertThat(domain.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
			assertThat(domain.code()).isEqualTo(ErrorCode.USER_NOT_FOUND);
		});
	}

	@Test
	void requireExistsPassesForKnownUser() {
		UUID id = UUID.randomUUID();
		when(userRepository.existsById(id)).thenReturn(true);

		Lookups.requireUser(userRepository, id);

		verify(userRepository).existsById(id);
	}

	@Test
	void requireExistsOnUnknownUserIsUserNotFound() {
		UUID id = UUID.randomUUID();
		when(userRepository.existsById(id)).thenReturn(false);

		assertThatThrownBy(() -> Lookups.requireUser(userRepository, id)).isInstanceOf(DomainException.class)
				.satisfies(ex -> {
					DomainException domain = (DomainException) ex;
					assertThat(domain.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
					assertThat(domain.code()).isEqualTo(ErrorCode.USER_NOT_FOUND);
				});
	}

	@Test
	void requireExistsAndLockPassesForKnownUser() {
		UUID id = UUID.randomUUID();
		when(userRepository.lockById(id)).thenReturn(true);

		Lookups.requireUserForUpdate(userRepository, id);

		verify(userRepository).lockById(id);
	}

	@Test
	void requireExistsAndLockOnUnknownUserIsUserNotFound() {
		UUID id = UUID.randomUUID();
		when(userRepository.lockById(id)).thenReturn(false);

		assertThatThrownBy(() -> Lookups.requireUserForUpdate(userRepository, id)).isInstanceOf(DomainException.class)
				.satisfies(ex -> {
					DomainException domain = (DomainException) ex;
					assertThat(domain.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
					assertThat(domain.code()).isEqualTo(ErrorCode.USER_NOT_FOUND);
				});
	}

}
