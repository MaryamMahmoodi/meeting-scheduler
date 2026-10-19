package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

class SlotIntervalTest {

	private static final Instant START = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant END = Instant.parse("2030-01-01T10:00:00Z");

	@Test
	void validIntervalIsKept() {
		SlotInterval interval = SlotInterval.of(START, END);

		assertThat(interval.start()).isEqualTo(START);
		assertThat(interval.end()).isEqualTo(END);
	}

	@Test
	void bothBoundsAreTruncatedToMicroseconds() {
		SlotInterval interval = SlotInterval.of(Instant.parse("2030-01-01T09:00:00.123456789Z"),
				Instant.parse("2030-01-01T10:00:00.987654321Z"));

		assertThat(interval.start()).isEqualTo(Instant.parse("2030-01-01T09:00:00.123456Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2030-01-01T10:00:00.987654Z"));
	}

	@Test
	void truncationHappensBeforeTheOrderCheck() {
		Instant start = Instant.parse("2030-01-01T09:00:00.000000100Z");
		Instant end = Instant.parse("2030-01-01T09:00:00.000000900Z");

		assertThatThrownBy(() -> SlotInterval.of(start, end)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
	}

	@Test
	void intervalOfExactlyOneMicrosecondIsAccepted() {
		SlotInterval interval = SlotInterval.of(Instant.parse("2030-01-01T09:00:00.000000999Z"),
				Instant.parse("2030-01-01T09:00:00.000001000Z"));

		assertThat(interval.start()).isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
		assertThat(interval.end()).isEqualTo(Instant.parse("2030-01-01T09:00:00.000001Z"));
	}

	@Test
	void supportedExtremesAreAccepted() {
		SlotInterval interval = SlotInterval.of(Timestamps.MIN, Timestamps.MAX);

		assertThat(interval.start()).isEqualTo(Timestamps.MIN);
		assertThat(interval.end()).isEqualTo(Timestamps.MAX);
	}

	@Test
	void unsupportedStartIsRejectedWithValidationErrorOnFieldStartTime() {
		assertThatThrownBy(() -> SlotInterval.of(Timestamps.MIN.minusNanos(1), END)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "startTime"));
		assertThatThrownBy(() -> SlotInterval.of(Timestamps.MAX.plusNanos(1), END)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "startTime"));
	}

	@Test
	void unsupportedEndIsRejectedWithValidationErrorOnFieldEndTime() {
		assertThatThrownBy(() -> SlotInterval.of(START, Timestamps.MAX.plusNanos(1))).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
		assertThatThrownBy(() -> SlotInterval.of(START, Timestamps.MIN.minusNanos(1))).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
	}

	@Test
	void endBeforeStartIsRejectedWithValidationErrorOnFieldEndTime() {
		assertThatThrownBy(() -> SlotInterval.of(END, START)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
	}

	@Test
	void endEqualToStartIsRejectedWithValidationErrorOnFieldEndTime() {
		assertThatThrownBy(() -> SlotInterval.of(START, START)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
	}

	private static void assertValidationOn(DomainException ex, String field) {
		assertThat(ex.kind()).isEqualTo(DomainException.Kind.VALIDATION);
		assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(ex.violations()).hasSize(1);
		assertThat(ex.violations().get(0).field()).isEqualTo(field);
	}

}
