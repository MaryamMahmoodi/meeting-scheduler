package com.demo.meetingscheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase.CreateTimeSlotCommand;
import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase.UpdateTimeSlotCommand;
import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

	private static final Instant START = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant END = Instant.parse("2030-01-01T10:00:00Z");
	private static final Instant CREATED_AT = Instant.parse("2029-12-01T00:00:00Z");

	private final UUID userId = UUID.randomUUID();
	private final UUID slotId = UUID.randomUUID();

	@Mock
	private TimeSlotRepository timeSlotRepository;

	@Mock
	private MeetingRepository meetingRepository;

	@Mock
	private UserRepository userRepository;

	private TimeSlotService service() {
		return new TimeSlotService(timeSlotRepository, meetingRepository, userRepository);
	}

	private TimeSlot slot(TimeSlotStatus status) {
		return new TimeSlot(slotId, userId, START, END, status, CREATED_AT);
	}

	private static void assertDomain(Throwable thrown, DomainException.Kind kind, ErrorCode code) {
		assertThat(thrown).isInstanceOf(DomainException.class);
		DomainException domain = (DomainException) thrown;
		assertThat(domain.kind()).isEqualTo(kind);
		assertThat(domain.code()).isEqualTo(code);
	}

	// ---- create ----

	@Test
	void createWithEndNotAfterStartIsValidationOnEndTimeBeforeAnyRepositoryCall() {
		CreateTimeSlotCommand request = new CreateTimeSlotCommand(END, START, null);

		assertThatThrownBy(() -> service().create(userId, request)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations()).hasSize(1);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("endTime");
		});

		verifyNoInteractions(timeSlotRepository, meetingRepository, userRepository);
	}

	@Test
	void createWithEndEqualToStartIsRejectedBeforeAnyRepositoryCall() {
		CreateTimeSlotCommand request = new CreateTimeSlotCommand(START, START, TimeSlotStatus.FREE);

		assertThatThrownBy(() -> service().create(userId, request)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("endTime");
		});

		verifyNoInteractions(timeSlotRepository, meetingRepository, userRepository);
	}

	@Test
	void createRejectsRangesThatOnlyDifferBelowMicrosecondPrecision() {
		CreateTimeSlotCommand request = new CreateTimeSlotCommand(Instant.parse("2030-01-01T09:00:00.000000100Z"),
				Instant.parse("2030-01-01T09:00:00.000000900Z"), null);

		assertThatThrownBy(() -> service().create(userId, request)).satisfies(
				thrown -> assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR));

		verifyNoInteractions(timeSlotRepository, meetingRepository, userRepository);
	}

	@Test
	void createForUnknownUserIsNotFoundBeforeTheOverlapCheck() {
		when(userRepository.lockById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().create(userId, new CreateTimeSlotCommand(START, END, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	@Test
	void createOverlappingExistingSlotIsConflictAndNothingIsSaved() {
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.existsOverlapping(userId, START, END)).thenReturn(true);

		assertThatThrownBy(() -> service().create(userId, new CreateTimeSlotCommand(START, END, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_OVERLAP));

		verify(timeSlotRepository, never()).save(any());
	}

	@Test
	void createChecksUserThenOverlapThenSaves() {
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		service().create(userId, new CreateTimeSlotCommand(START, END, null));

		InOrder order = inOrder(userRepository, timeSlotRepository);
		order.verify(userRepository).lockById(userId);
		order.verify(timeSlotRepository).existsOverlapping(userId, START, END);
		order.verify(timeSlotRepository).save(any(TimeSlot.class));
	}

	@Test
	void createWithoutStatusDefaultsToFree() {
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().create(userId, new CreateTimeSlotCommand(START, END, null));

		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().id()).isNull();
		assertThat(saved.getValue().status()).isEqualTo(TimeSlotStatus.FREE);
		assertThat(saved.getValue().userId()).isEqualTo(userId);
		assertThat(saved.getValue().startTime()).isEqualTo(START);
		assertThat(saved.getValue().endTime()).isEqualTo(END);
		assertThat(saved.getValue().createdAt()).isNotNull();
		assertThat(response.status()).isEqualTo(TimeSlotStatus.FREE);
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.startTime()).isEqualTo(START);
		assertThat(response.endTime()).isEqualTo(END);
	}

	@Test
	void createWithExplicitStatusKeepsIt() {
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().create(userId, new CreateTimeSlotCommand(START, END, TimeSlotStatus.BUSY));

		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(response.status()).isEqualTo(TimeSlotStatus.BUSY);
	}

	@Test
	void createTruncatesInputToMicrosecondsForOverlapCheckAndPersistence() {
		Instant rawStart = Instant.parse("2030-01-01T09:00:00.123456789Z");
		Instant rawEnd = Instant.parse("2030-01-01T10:00:00.987654321Z");
		Instant start = Instant.parse("2030-01-01T09:00:00.123456Z");
		Instant end = Instant.parse("2030-01-01T10:00:00.987654Z");
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().create(userId, new CreateTimeSlotCommand(rawStart, rawEnd, null));

		verify(timeSlotRepository).existsOverlapping(userId, start, end);
		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().startTime()).isEqualTo(start);
		assertThat(saved.getValue().endTime()).isEqualTo(end);
		assertThat(response.startTime()).isEqualTo(start);
		assertThat(response.endTime()).isEqualTo(end);
	}

	@Test
	void createOverlapQueryIsCalledWithUserIdStartThenEnd() {
		// existing.start < requested.end AND existing.end > requested.start
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		service().create(userId, new CreateTimeSlotCommand(START, END, null));

		verify(timeSlotRepository).existsOverlapping(userId, START, END);
	}

	// ---- update ----

	@Test
	void updateWithEndNotAfterStartIsValidationBeforeAnyRepositoryCall() {
		UpdateTimeSlotCommand request = new UpdateTimeSlotCommand(END, START, null);

		assertThatThrownBy(() -> service().update(userId, slotId, request)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("endTime");
		});

		verifyNoInteractions(timeSlotRepository, meetingRepository, userRepository);
	}

	@Test
	void updateForUnknownUserIsNotFoundBeforeSlotLookup() {
		when(userRepository.lockById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().update(userId, slotId, new UpdateTimeSlotCommand(START, END, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	@Test
	void updateLooksTheSlotUpThroughItsOwnerAndMissingSlotIsNotFound() {
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().update(userId, slotId, new UpdateTimeSlotCommand(START, END, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));

		verify(timeSlotRepository).lockByIdAndUserId(slotId, userId);
		verify(timeSlotRepository, never()).findByIdAndUserId(any(), any());
		verifyNoInteractions(meetingRepository);
		verify(timeSlotRepository, never()).save(any());
	}

	@Test
	void updateOfBookedSlotIsSlotBookedEvenIfTheNewRangeAlsoOverlaps() {
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(meetingRepository.existsBySlotId(slotId)).thenReturn(true);

		assertThatThrownBy(() -> service().update(userId, slotId, new UpdateTimeSlotCommand(
				Instant.parse("2030-01-01T11:00:00Z"), Instant.parse("2030-01-01T12:00:00Z"), null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_BOOKED));

		verify(timeSlotRepository, never()).existsOverlappingExcluding(any(), any(), any(), any());
		verify(timeSlotRepository, never()).save(any());
		assertThat(slot.startTime()).isEqualTo(START);
		assertThat(slot.endTime()).isEqualTo(END);
	}

	@Test
	void updateOverlapCheckExcludesTheSlotItself() {
		Instant newStart = Instant.parse("2030-01-01T09:30:00Z");
		Instant newEnd = Instant.parse("2030-01-01T10:30:00Z");
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		service().update(userId, slotId, new UpdateTimeSlotCommand(newStart, newEnd, null));

		verify(timeSlotRepository).existsOverlappingExcluding(userId, slotId, newStart, newEnd);
		verify(timeSlotRepository, never()).existsOverlapping(any(), any(), any());
	}

	@Test
	void updateOverlappingAnotherSlotIsConflictAndSlotIsNotChanged() {
		Instant newStart = Instant.parse("2030-01-01T09:30:00Z");
		Instant newEnd = Instant.parse("2030-01-01T10:30:00Z");
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.existsOverlappingExcluding(userId, slotId, newStart, newEnd)).thenReturn(true);

		assertThatThrownBy(() -> service().update(userId, slotId, new UpdateTimeSlotCommand(newStart, newEnd, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_OVERLAP));

		verify(timeSlotRepository, never()).save(any());
		assertThat(slot.startTime()).isEqualTo(START);
		assertThat(slot.endTime()).isEqualTo(END);
	}

	@Test
	void updateKeepsTheStatusWhenRequestStatusIsNull() {
		Instant newStart = Instant.parse("2030-01-01T11:00:00Z");
		Instant newEnd = Instant.parse("2030-01-01T12:00:00Z");
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().update(userId, slotId, new UpdateTimeSlotCommand(newStart, newEnd, null));

		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(saved.getValue().startTime()).isEqualTo(newStart);
		assertThat(saved.getValue().endTime()).isEqualTo(newEnd);
		assertThat(response.status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(response.startTime()).isEqualTo(newStart);
		assertThat(response.endTime()).isEqualTo(newEnd);
	}

	@Test
	void updateAppliesAnExplicitStatus() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().update(userId, slotId,
				new UpdateTimeSlotCommand(START, END, TimeSlotStatus.BUSY));

		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(response.status()).isEqualTo(TimeSlotStatus.BUSY);
	}

	@Test
	void updateTruncatesInputToMicroseconds() {
		Instant rawStart = Instant.parse("2030-01-01T11:00:00.123456789Z");
		Instant rawEnd = Instant.parse("2030-01-01T12:00:00.987654321Z");
		Instant start = Instant.parse("2030-01-01T11:00:00.123456Z");
		Instant end = Instant.parse("2030-01-01T12:00:00.987654Z");
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		service().update(userId, slotId, new UpdateTimeSlotCommand(rawStart, rawEnd, null));

		verify(timeSlotRepository).existsOverlappingExcluding(userId, slotId, start, end);
		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().startTime()).isEqualTo(start);
		assertThat(saved.getValue().endTime()).isEqualTo(end);
	}

	@Test
	void updateKeepsIdOwnerAndCreationTimeOfTheSlot() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.lockById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().update(userId, slotId,
				new UpdateTimeSlotCommand(Instant.parse("2030-01-01T11:00:00Z"),
						Instant.parse("2030-01-01T12:00:00Z"), null));

		assertThat(response.id()).isEqualTo(slotId);
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
	}

	// ---- updateAvailability ----

	@Test
	void updateAvailabilityOnBookedSlotIsSlotBooked() {
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(meetingRepository.existsBySlotId(slotId)).thenReturn(true);

		assertThatThrownBy(() -> service().updateAvailability(userId, slotId, TimeSlotStatus.FREE))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_BOOKED));

		assertThat(slot.status()).isEqualTo(TimeSlotStatus.BUSY);
		verify(timeSlotRepository, never()).save(any());
	}

	@Test
	void updateAvailabilityOnUnbookedSlotChangesStatus() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

		TimeSlot response = service().updateAvailability(userId, slotId, TimeSlotStatus.BUSY);

		ArgumentCaptor<TimeSlot> saved = ArgumentCaptor.forClass(TimeSlot.class);
		verify(timeSlotRepository).save(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(response.status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(response.startTime()).isEqualTo(START);
		assertThat(response.endTime()).isEqualTo(END);
	}

	@Test
	void updateAvailabilityLooksTheSlotUpThroughItsOwnerAndMissingSlotIsNotFound() {
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().updateAvailability(userId, slotId, TimeSlotStatus.BUSY))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));

		verify(timeSlotRepository).lockByIdAndUserId(slotId, userId);
		verify(timeSlotRepository, never()).save(any());
	}

	@Test
	void updateAvailabilityForUnknownUserIsNotFoundBeforeSlotLookup() {
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().updateAvailability(userId, slotId, TimeSlotStatus.BUSY))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	// ---- delete ----

	@Test
	void deleteOfBookedSlotIsSlotBookedAndNothingIsDeleted() {
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(meetingRepository.existsBySlotId(slotId)).thenReturn(true);

		assertThatThrownBy(() -> service().delete(userId, slotId))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_BOOKED));

		verify(timeSlotRepository, never()).delete(any());
	}

	@Test
	void deleteOfUnbookedSlotDeletesIt() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));
		when(meetingRepository.existsBySlotId(slotId)).thenReturn(false);

		service().delete(userId, slotId);

		verify(timeSlotRepository).delete(slot);
	}

	@Test
	void deleteLooksTheSlotUpThroughItsOwnerAndMissingSlotIsNotFound() {
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.lockByIdAndUserId(slotId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().delete(userId, slotId))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));

		verify(timeSlotRepository).lockByIdAndUserId(slotId, userId);
		verify(timeSlotRepository, never()).delete(any());
		verifyNoInteractions(meetingRepository);
	}

	@Test
	void deleteForUnknownUserIsNotFoundBeforeSlotLookup() {
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().delete(userId, slotId))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	// ---- get / list ----

	@Test
	void getReturnsTheSlotOfTheUser() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.findByIdAndUserId(slotId, userId)).thenReturn(Optional.of(slot));

		TimeSlot response = service().get(userId, slotId);

		assertThat(response.id()).isEqualTo(slotId);
		assertThat(response.userId()).isEqualTo(userId);
	}

	@Test
	void getOfMissingOrForeignSlotIsSlotNotFound() {
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.findByIdAndUserId(slotId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().get(userId, slotId))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));
	}

	@Test
	void getForUnknownUserIsUserNotFoundBeforeSlotLookup() {
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().get(userId, slotId))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	@Test
	void listValidatesTheRangeBeforeLookingUpTheUser() {
		assertThatThrownBy(() -> service().list(userId, END, START)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("to");
		});

		verifyNoInteractions(userRepository, timeSlotRepository);
	}

	@Test
	void listWithEqualBoundsIsRejected() {
		assertThatThrownBy(() -> service().list(userId, START, START)).satisfies(
				thrown -> assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR));

		verifyNoInteractions(userRepository, timeSlotRepository);
	}

	@Test
	void listForUnknownUserIsNotFoundWithoutQueryingSlots() {
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> service().list(userId, START, END))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(timeSlotRepository);
	}

	@Test
	void listQueriesWithTheTruncatedRangeAndMapsTheResult() {
		Instant rawFrom = Instant.parse("2030-01-01T00:00:00.123456789Z");
		Instant rawTo = Instant.parse("2030-01-02T00:00:00.123456789Z");
		Instant from = Instant.parse("2030-01-01T00:00:00.123456Z");
		Instant to = Instant.parse("2030-01-02T00:00:00.123456Z");
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(userRepository.existsById(userId)).thenReturn(true);
		when(timeSlotRepository.findIntersecting(userId, new TimeRange(from, to))).thenReturn(List.of(slot));

		List<TimeSlot> result = service().list(userId, rawFrom, rawTo);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo(slotId);
		verify(timeSlotRepository).findIntersecting(userId, new TimeRange(from, to));
		verifyNoMoreInteractions(timeSlotRepository);
	}

}
