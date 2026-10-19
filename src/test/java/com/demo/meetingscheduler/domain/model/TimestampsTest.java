package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

class TimestampsTest {

	@Test
	void minAndMaxAreInclusive() {
		assertThat(Timestamps.requireSupported("startTime", Timestamps.MIN)).isEqualTo(Timestamps.MIN);
		assertThat(Timestamps.requireSupported("startTime", Timestamps.MAX)).isEqualTo(Timestamps.MAX);
	}

	@Test
	void justBeforeMinIsRejectedWithValidationErrorOnTheGivenField() {
		assertThatThrownBy(() -> Timestamps.requireSupported("startTime", Timestamps.MIN.minusNanos(1)))
				.isInstanceOf(DomainException.class).satisfies(ex -> assertValidationOn((DomainException) ex, "startTime"));
	}

	@Test
	void justAfterMaxIsRejectedWithValidationErrorOnTheGivenField() {
		assertThatThrownBy(() -> Timestamps.requireSupported("endTime", Timestamps.MAX.plusNanos(1)))
				.isInstanceOf(DomainException.class).satisfies(ex -> assertValidationOn((DomainException) ex, "endTime"));
	}

	@Test
	void farOutsideTheSupportedRangeIsRejected() {
		assertThatThrownBy(() -> Timestamps.requireSupported("from", Instant.MIN)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Timestamps.requireSupported("to", Instant.MAX)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectionMessageMentionsTheSupportedBounds() {
		assertThatThrownBy(() -> Timestamps.requireSupported("from", Timestamps.MAX.plusSeconds(1)))
				.hasMessageContaining(Timestamps.MIN.toString()).hasMessageContaining(Timestamps.MAX.toString());
	}

	@Test
	void supportedValueIsTruncatedToMicroseconds() {
		Instant value = Instant.parse("2030-01-01T09:00:00.123456789Z");

		assertThat(Timestamps.requireSupported("from", value)).isEqualTo(Instant.parse("2030-01-01T09:00:00.123456Z"));
	}

	@Test
	void truncateDropsEverythingBelowMicroseconds() {
		assertThat(Timestamps.truncate(Instant.parse("2030-01-01T09:00:00.000000999Z")))
				.isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
		assertThat(Timestamps.truncate(Instant.parse("2030-01-01T09:00:00.000001Z")))
				.isEqualTo(Instant.parse("2030-01-01T09:00:00.000001Z"));
	}

	@Test
	void nowIsTruncatedToMicroseconds() {
		Instant now = Timestamps.now();

		assertThat(now).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
	}

	private static void assertValidationOn(DomainException ex, String field) {
		assertThat(ex.kind()).isEqualTo(DomainException.Kind.VALIDATION);
		assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(ex.violations()).hasSize(1);
		assertThat(ex.violations().get(0).field()).isEqualTo(field);
	}

}
