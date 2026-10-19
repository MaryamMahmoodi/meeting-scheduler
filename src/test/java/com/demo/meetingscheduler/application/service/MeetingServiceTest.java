package com.demo.meetingscheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.meetingscheduler.application.port.in.ManageMeetingsUseCase.CreateMeetingCommand;
import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.Meeting;
import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

	private static final Instant START = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant END = Instant.parse("2030-01-01T10:00:00Z");

	private final UUID organizerId = UUID.randomUUID();
	private final UUID slotId = UUID.randomUUID();

	@Mock
	private MeetingRepository meetings;

	@Mock
	private TimeSlotRepository slots;

	@Mock
	private UserRepository users;

	private MeetingService service() {
		return new MeetingService(meetings, slots, users);
	}

	private TimeSlot slot(TimeSlotStatus status) {
		return new TimeSlot(slotId, organizerId, START, END, status, Instant.parse("2029-12-01T00:00:00Z"));
	}

	private static void assertDomain(Throwable thrown, DomainException.Kind kind, ErrorCode code) {
		assertThat(thrown).isInstanceOf(DomainException.class);
		DomainException domain = (DomainException) thrown;
		assertThat(domain.kind()).isEqualTo(kind);
		assertThat(domain.code()).isEqualTo(code);
	}

	private void verifyNothingTouched() {
		verifyNoInteractions(users, slots, meetings);
	}

	// ---- create ----

	@Test
	void createBooksFreeSlotMarksItBusyAndSavesMeetingFromSlotTimes() {
		UUID p1 = UUID.randomUUID();
		UUID p2 = UUID.randomUUID();
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(users.findExistingIds(Set.of(p1, p2))).thenReturn(Set.of(p1, p2));
		when(slots.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));
		when(meetings.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

		Meeting response = service().create(organizerId, new CreateMeetingCommand(slotId, "Planning", "Quarterly",
				List.of(p1, p2)));

		ArgumentCaptor<TimeSlot> savedSlot = ArgumentCaptor.forClass(TimeSlot.class);
		verify(slots).save(savedSlot.capture());
		assertThat(savedSlot.getValue().id()).isEqualTo(slotId);
		assertThat(savedSlot.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		ArgumentCaptor<Meeting> saved = ArgumentCaptor.forClass(Meeting.class);
		verify(meetings).save(saved.capture());
		Meeting meeting = saved.getValue();
		assertThat(meeting.organizerId()).isEqualTo(organizerId);
		assertThat(meeting.slotId()).isEqualTo(slotId);
		assertThat(meeting.title()).isEqualTo("Planning");
		assertThat(meeting.description()).isEqualTo("Quarterly");
		assertThat(meeting.startTime()).isEqualTo(START);
		assertThat(meeting.endTime()).isEqualTo(END);
		assertThat(meeting.participantIds()).containsExactlyInAnyOrder(p1, p2);
		assertThat(meeting.createdAt()).isNotNull();
		assertThat(response.organizerId()).isEqualTo(organizerId);
		assertThat(response.slotId()).isEqualTo(slotId);
		assertThat(response.startTime()).isEqualTo(START);
		assertThat(response.endTime()).isEqualTo(END);
		assertThat(response.participantIds()).containsExactlyInAnyOrder(p1, p2);
	}

	@Test
	void createVerifiesTheOrganizerExists() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(slots.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));
		when(meetings.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

		service().create(organizerId, new CreateMeetingCommand(slotId, "t", null, null));

		verify(users).existsById(organizerId);
	}

	@Test
	void createWithNullParticipantIdsMeansNoParticipants() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(slots.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));
		when(meetings.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

		Meeting response = service().create(organizerId, new CreateMeetingCommand(slotId, "Solo", null, null));

		ArgumentCaptor<Meeting> saved = ArgumentCaptor.forClass(Meeting.class);
		verify(meetings).save(saved.capture());
		assertThat(saved.getValue().participantIds()).isEmpty();
		assertThat(response.participantIds()).isEmpty();
		ArgumentCaptor<TimeSlot> savedSlot = ArgumentCaptor.forClass(TimeSlot.class);
		verify(slots).save(savedSlot.capture());
		assertThat(savedSlot.getValue().status()).isEqualTo(TimeSlotStatus.BUSY);
		verify(users, never()).findExistingIds(any());
	}

	@Test
	void createWithEmptyParticipantIdsDoesNotQueryUsers() {
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(slots.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));
		when(meetings.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

		Meeting response = service().create(organizerId, new CreateMeetingCommand(slotId, "Solo", null, List.of()));

		assertThat(response.participantIds()).isEmpty();
		verify(users, never()).findExistingIds(any());
	}

	@Test
	void createFromBusySlotIsSlotNotFreeAndNothingIsSaved() {
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));

		assertThatThrownBy(() -> service().create(organizerId, new CreateMeetingCommand(slotId, "t", null, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.CONFLICT, ErrorCode.SLOT_NOT_FREE));

		assertThat(slot.status()).isEqualTo(TimeSlotStatus.BUSY);
		verify(slots, never()).save(any());
		verify(meetings, never()).save(any());
	}

	@Test
	void createWithOrganizerAmongParticipantsIsValidationBeforeAnyRepositoryAccess() {
		UUID other = UUID.randomUUID();
		CreateMeetingCommand command = new CreateMeetingCommand(slotId, "t", null, List.of(other, organizerId));

		assertThatThrownBy(() -> service().create(organizerId, command)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations()).hasSize(1);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("participantIds");
		});

		verifyNothingTouched();
	}

	@Test
	void createWithDuplicateParticipantsIsValidationBeforeAnyRepositoryAccess() {
		UUID other = UUID.randomUUID();
		CreateMeetingCommand command = new CreateMeetingCommand(slotId, "t", null, List.of(other, other));

		assertThatThrownBy(() -> service().create(organizerId, command)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("participantIds");
		});

		verifyNothingTouched();
	}

	@Test
	void createValidationWinsOverUnknownOrganizer() {
		CreateMeetingCommand command = new CreateMeetingCommand(slotId, "t", null, List.of(organizerId));

		assertThatThrownBy(() -> service().create(organizerId, command)).satisfies(
				thrown -> assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR));

		verifyNoInteractions(users);
	}

	@Test
	void createForUnknownOrganizerIsUserNotFoundBeforeSlotLookup() {
		when(users.existsById(organizerId)).thenReturn(false);

		assertThatThrownBy(() -> service().create(organizerId, new CreateMeetingCommand(slotId, "t", null, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(slots, meetings);
	}

	@Test
	void createWithMissingOrForeignSlotIsSlotNotFoundLookedUpThroughTheOrganizer() {
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().create(organizerId, new CreateMeetingCommand(slotId, "t", null, null)))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));

		verify(slots).lockByIdAndUserId(slotId, organizerId);
		verify(meetings, never()).save(any());
	}

	@Test
	void createSlotNotFoundIsReportedBeforeUnknownParticipants() {
		UUID ghost = UUID.randomUUID();
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().create(organizerId,
				new CreateMeetingCommand(slotId, "t", null, List.of(ghost))))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.SLOT_NOT_FOUND));

		verify(users, never()).findExistingIds(any());
	}

	@Test
	void createWithUnknownParticipantsIsUserNotFoundListingThemAndSlotIsNotModified() {
		UUID known = UUID.randomUUID();
		UUID ghost1 = UUID.randomUUID();
		UUID ghost2 = UUID.randomUUID();
		TimeSlot slot = slot(TimeSlotStatus.FREE);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(users.findExistingIds(Set.of(known, ghost1, ghost2))).thenReturn(Set.of(known));

		assertThatThrownBy(() -> service().create(organizerId,
				new CreateMeetingCommand(slotId, "t", null, List.of(known, ghost1, ghost2)))).satisfies(thrown -> {
					assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND);
					String detail = thrown.getMessage();
					assertThat(detail).contains(ghost1.toString()).contains(ghost2.toString())
							.doesNotContain(known.toString());
				});

		assertThat(slot.status()).isEqualTo(TimeSlotStatus.FREE);
		verify(slots, never()).save(any());
		verify(meetings, never()).save(any());
	}

	@Test
	void createUnknownParticipantsAreReportedBeforeSlotNotFree() {
		UUID ghost = UUID.randomUUID();
		TimeSlot slot = slot(TimeSlotStatus.BUSY);
		when(users.existsById(organizerId)).thenReturn(true);
		when(slots.lockByIdAndUserId(slotId, organizerId)).thenReturn(Optional.of(slot));
		when(users.findExistingIds(Set.of(ghost))).thenReturn(Set.of());

		assertThatThrownBy(() -> service().create(organizerId,
				new CreateMeetingCommand(slotId, "t", null, List.of(ghost))))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		assertThat(slot.status()).isEqualTo(TimeSlotStatus.BUSY);
		verify(slots, never()).save(any());
		verify(meetings, never()).save(any());
	}

	// ---- list / get ----

	@Test
	void listValidatesTheRangeBeforeLookingUpTheUser() {
		assertThatThrownBy(() -> service().list(organizerId, END, START)).satisfies(thrown -> {
			assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR);
			assertThat(((DomainException) thrown).violations().get(0).field()).isEqualTo("to");
		});

		verifyNoInteractions(users, meetings);
	}

	@Test
	void listWithEqualBoundsIsRejected() {
		assertThatThrownBy(() -> service().list(organizerId, START, START)).satisfies(
				thrown -> assertDomain(thrown, DomainException.Kind.VALIDATION, ErrorCode.VALIDATION_ERROR));

		verifyNoInteractions(users, meetings);
	}

	@Test
	void listForUnknownUserIsNotFoundWithoutQueryingMeetings() {
		when(users.existsById(organizerId)).thenReturn(false);

		assertThatThrownBy(() -> service().list(organizerId, START, END))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(meetings);
	}

	// Sorting the participants is a presentation concern now: the use case returns the meeting with its ID set.
	@Test
	void listQueriesWithTheTruncatedRangeAndReturnsTheMeetings() {
		Instant rawFrom = Instant.parse("2030-01-01T00:00:00.123456789Z");
		Instant rawTo = Instant.parse("2030-01-02T00:00:00.123456789Z");
		Instant from = Instant.parse("2030-01-01T00:00:00.123456Z");
		Instant to = Instant.parse("2030-01-02T00:00:00.123456Z");
		UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
		UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000001");
		Meeting meeting = new Meeting(UUID.randomUUID(), organizerId, slotId, "t", null, START, END, Set.of(p1, p2),
				START);
		when(users.existsById(organizerId)).thenReturn(true);
		when(meetings.findForUserIntersecting(organizerId, new TimeRange(from, to))).thenReturn(List.of(meeting));

		List<Meeting> result = service().list(organizerId, rawFrom, rawTo);

		assertThat(result).containsExactly(meeting);
		assertThat(result.get(0).participantIds()).containsExactlyInAnyOrder(p1, p2);
		verify(meetings).findForUserIntersecting(organizerId, new TimeRange(from, to));
	}

	@Test
	void getReturnsTheMeeting() {
		UUID meetingId = UUID.randomUUID();
		Meeting meeting = new Meeting(meetingId, organizerId, slotId, "Title", "Desc", START, END, Set.of(), START);
		when(users.existsById(organizerId)).thenReturn(true);
		when(meetings.findByIdForUser(meetingId, organizerId)).thenReturn(Optional.of(meeting));

		Meeting response = service().get(organizerId, meetingId);

		assertThat(response.id()).isEqualTo(meetingId);
		assertThat(response.title()).isEqualTo("Title");
		assertThat(response.description()).isEqualTo("Desc");
	}

	@Test
	void getOfUnknownMeetingIsMeetingNotFound() {
		UUID meetingId = UUID.randomUUID();
		when(users.existsById(organizerId)).thenReturn(true);
		when(meetings.findByIdForUser(meetingId, organizerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().get(organizerId, meetingId)).satisfies(
				thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.MEETING_NOT_FOUND));
	}

	@Test
	void getForUnknownUserIsUserNotFoundBeforeMeetingLookup() {
		when(users.existsById(organizerId)).thenReturn(false);

		assertThatThrownBy(() -> service().get(organizerId, UUID.randomUUID()))
				.satisfies(thrown -> assertDomain(thrown, DomainException.Kind.NOT_FOUND, ErrorCode.USER_NOT_FOUND));

		verifyNoInteractions(meetings);
	}

}
