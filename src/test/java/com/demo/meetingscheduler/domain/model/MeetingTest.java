package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MeetingTest {

	private static final UUID ORGANIZER_ID = UUID.randomUUID();
	private static final Instant START = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant END = Instant.parse("2030-01-01T10:00:00Z");

	private static TimeSlot slot() {
		return new TimeSlot(UUID.randomUUID(), ORGANIZER_ID, START, END, TimeSlotStatus.BUSY,
				Instant.parse("2029-12-31T12:00:00Z"));
	}

	@Test
	void bookTakesSlotIdAndIntervalFromTheSlot() {
		TimeSlot slot = slot();

		Meeting meeting = Meeting.book(ORGANIZER_ID, slot, "Planning", "Sprint planning", Set.of());

		assertThat(meeting.slotId()).isEqualTo(slot.id());
		assertThat(meeting.startTime()).isEqualTo(START);
		assertThat(meeting.endTime()).isEqualTo(END);
		assertThat(meeting.organizerId()).isEqualTo(ORGANIZER_ID);
		assertThat(meeting.title()).isEqualTo("Planning");
		assertThat(meeting.description()).isEqualTo("Sprint planning");
	}

	@Test
	void bookLeavesTheIdUnsetAndTruncatesCreatedAtToMicroseconds() {
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Meeting meeting = Meeting.book(ORGANIZER_ID, slot(), "Planning", null, Set.of());

		Instant after = Instant.now();
		assertThat(meeting.id()).isNull();
		assertThat(meeting.description()).isNull();
		assertThat(meeting.createdAt()).isEqualTo(meeting.createdAt().truncatedTo(ChronoUnit.MICROS));
		assertThat(meeting.createdAt()).isBetween(before, after);
	}

	@Test
	void participantsAreCopiedSoLaterChangesToTheSourceDoNotLeakIn() {
		UUID first = UUID.randomUUID();
		Set<UUID> participants = new HashSet<>(Set.of(first));

		Meeting meeting = Meeting.book(ORGANIZER_ID, slot(), "Planning", null, participants);
		participants.add(UUID.randomUUID());

		assertThat(meeting.participantIds()).containsExactly(first);
	}

	@Test
	void participantsAreImmutable() {
		Meeting meeting = Meeting.book(ORGANIZER_ID, slot(), "Planning", null, Set.of(UUID.randomUUID()));

		assertThatThrownBy(() -> meeting.participantIds().add(UUID.randomUUID()))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> meeting.participantIds().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void noParticipantsIsAllowed() {
		Meeting meeting = Meeting.book(ORGANIZER_ID, slot(), "Planning", null, Set.of());

		assertThat(meeting.participantIds()).isEmpty();
	}

}
