package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

class TimeSlotTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final Instant START = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant END = Instant.parse("2030-01-01T10:00:00Z");
	private static final Instant CREATED_AT = Instant.parse("2029-12-31T12:00:00Z");

	private static TimeSlot slot(TimeSlotStatus status) {
		return new TimeSlot(UUID.randomUUID(), USER_ID, START, END, status, CREATED_AT);
	}

	@Test
	void createWithoutStatusDefaultsToFree() {
		TimeSlot slot = TimeSlot.create(USER_ID, SlotInterval.of(START, END), null);

		assertThat(slot.status()).isEqualTo(TimeSlotStatus.FREE);
	}

	@Test
	void createKeepsAnExplicitStatus() {
		assertThat(TimeSlot.create(USER_ID, SlotInterval.of(START, END), TimeSlotStatus.BUSY).status())
				.isEqualTo(TimeSlotStatus.BUSY);
		assertThat(TimeSlot.create(USER_ID, SlotInterval.of(START, END), TimeSlotStatus.FREE).status())
				.isEqualTo(TimeSlotStatus.FREE);
	}

	@Test
	void createTakesUserAndIntervalAndLeavesTheIdUnset() {
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		TimeSlot slot = TimeSlot.create(USER_ID, SlotInterval.of(START, END), null);

		Instant after = Instant.now();
		assertThat(slot.id()).isNull();
		assertThat(slot.userId()).isEqualTo(USER_ID);
		assertThat(slot.startTime()).isEqualTo(START);
		assertThat(slot.endTime()).isEqualTo(END);
		assertThat(slot.createdAt()).isEqualTo(slot.createdAt().truncatedTo(ChronoUnit.MICROS));
		assertThat(slot.createdAt()).isBetween(before, after);
	}

	@Test
	void rescheduleChangesOnlyTheInterval() {
		TimeSlot original = slot(TimeSlotStatus.BUSY);
		Instant newStart = Instant.parse("2030-02-01T09:00:00Z");
		Instant newEnd = Instant.parse("2030-02-01T11:00:00Z");

		TimeSlot moved = original.reschedule(SlotInterval.of(newStart, newEnd));

		assertThat(moved.startTime()).isEqualTo(newStart);
		assertThat(moved.endTime()).isEqualTo(newEnd);
		assertThat(moved.id()).isEqualTo(original.id());
		assertThat(moved.userId()).isEqualTo(original.userId());
		assertThat(moved.status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(moved.createdAt()).isEqualTo(CREATED_AT);
		// the original is immutable
		assertThat(original.startTime()).isEqualTo(START);
		assertThat(original.endTime()).isEqualTo(END);
	}

	@Test
	void withStatusChangesOnlyTheStatus() {
		TimeSlot original = slot(TimeSlotStatus.FREE);

		TimeSlot busy = original.withStatus(TimeSlotStatus.BUSY);

		assertThat(busy).isEqualTo(new TimeSlot(original.id(), USER_ID, START, END, TimeSlotStatus.BUSY, CREATED_AT));
		assertThat(original.status()).isEqualTo(TimeSlotStatus.FREE);
	}

	@Test
	void bookingAFreeSlotMakesItBusy() {
		TimeSlot free = slot(TimeSlotStatus.FREE);

		TimeSlot booked = free.book();

		assertThat(booked.status()).isEqualTo(TimeSlotStatus.BUSY);
		assertThat(booked.id()).isEqualTo(free.id());
		assertThat(booked.startTime()).isEqualTo(START);
		assertThat(booked.endTime()).isEqualTo(END);
		assertThat(free.status()).isEqualTo(TimeSlotStatus.FREE);
	}

	@Test
	void bookingABusySlotIsAConflictMentioningIdAndStatus() {
		TimeSlot busy = slot(TimeSlotStatus.BUSY);

		assertThatThrownBy(busy::book).isInstanceOf(DomainException.class).satisfies(ex -> {
			DomainException domain = (DomainException) ex;
			assertThat(domain.kind()).isEqualTo(DomainException.Kind.CONFLICT);
			assertThat(domain.code()).isEqualTo(ErrorCode.SLOT_NOT_FREE);
			assertThat(domain.getMessage()).contains(busy.id().toString()).contains("BUSY");
		});
	}

}
