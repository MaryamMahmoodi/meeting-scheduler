package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

class TimeRangeTest {

	private static final Instant FROM = Instant.parse("2030-01-01T09:00:00Z");
	private static final Instant TO = Instant.parse("2030-01-01T10:00:00Z");

	@Test
	void validRangeIsKept() {
		TimeRange range = TimeRange.of(FROM, TO);

		assertThat(range.from()).isEqualTo(FROM);
		assertThat(range.to()).isEqualTo(TO);
	}

	@Test
	void bothBoundsAreTruncatedToMicroseconds() {
		TimeRange range = TimeRange.of(Instant.parse("2030-01-01T09:00:00.123456789Z"),
				Instant.parse("2030-01-01T10:00:00.987654321Z"));

		assertThat(range.from()).isEqualTo(Instant.parse("2030-01-01T09:00:00.123456Z"));
		assertThat(range.to()).isEqualTo(Instant.parse("2030-01-01T10:00:00.987654Z"));
	}

	@Test
	void truncationHappensBeforeTheOrderCheck() {
		// Differ only below microsecond precision: after truncation the bounds are equal, so the range is empty.
		Instant from = Instant.parse("2030-01-01T09:00:00.000000100Z");
		Instant to = Instant.parse("2030-01-01T09:00:00.000000900Z");

		assertThatThrownBy(() -> TimeRange.of(from, to)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOnTo((DomainException) ex));
	}

	@Test
	void rangeOfExactlyOneMicrosecondIsAccepted() {
		Instant from = Instant.parse("2030-01-01T09:00:00.000000999Z");
		Instant to = Instant.parse("2030-01-01T09:00:00.000001000Z");

		TimeRange range = TimeRange.of(from, to);

		assertThat(range.from()).isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
		assertThat(range.to()).isEqualTo(Instant.parse("2030-01-01T09:00:00.000001Z"));
	}

	@Test
	void fromAfterToIsRejectedWithValidationErrorOnFieldTo() {
		assertThatThrownBy(() -> TimeRange.of(TO, FROM)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOnTo((DomainException) ex));
	}

	@Test
	void fromEqualToToIsRejectedWithValidationErrorOnFieldTo() {
		assertThatThrownBy(() -> TimeRange.of(FROM, FROM)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOnTo((DomainException) ex));
	}

	@Test
	void unsupportedFromIsRejectedWithValidationErrorOnFieldFrom() {
		assertThatThrownBy(() -> TimeRange.of(Timestamps.MIN.minusNanos(1), TO)).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "from"));
	}

	@Test
	void unsupportedToIsRejectedWithValidationErrorOnFieldTo() {
		assertThatThrownBy(() -> TimeRange.of(FROM, Timestamps.MAX.plusNanos(1))).isInstanceOf(DomainException.class)
				.satisfies(ex -> assertValidationOn((DomainException) ex, "to"));
	}

	@Test
	void supportedExtremesAreAccepted() {
		TimeRange range = TimeRange.of(Timestamps.MIN, Timestamps.MAX);

		assertThat(range.from()).isEqualTo(Timestamps.MIN);
		assertThat(range.to()).isEqualTo(Timestamps.MAX);
	}

	private static void assertValidationOnTo(DomainException ex) {
		assertValidationOn(ex, "to");
	}

	private static void assertValidationOn(DomainException ex, String field) {
		assertThat(ex.kind()).isEqualTo(DomainException.Kind.VALIDATION);
		assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(ex.violations()).hasSize(1);
		assertThat(ex.violations().get(0).field()).isEqualTo(field);
	}

}
