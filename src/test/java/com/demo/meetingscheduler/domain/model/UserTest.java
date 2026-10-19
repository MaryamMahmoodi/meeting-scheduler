package com.demo.meetingscheduler.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void registerKeepsTheName() {
		User user = User.register("Alice", "alice@example.com");

		assertThat(user.name()).isEqualTo("Alice");
	}

	@Test
	void registerLowerCasesTheEmail() {
		User user = User.register("Alice", "Alice@EXAMPLE.Com");

		assertThat(user.email()).isEqualTo("alice@example.com");
	}

	@Test
	void registerLowerCasesWithRootLocaleSemantics() {
		// Under a Turkish locale "I".toLowerCase() would yield a dotless i; ROOT must be used.
		Locale previous = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			User user = User.register("Ida", "INFO@EXAMPLE.COM");

			assertThat(user.email()).isEqualTo("info@example.com");
		}
		finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void registerLeavesTheIdUnset() {
		User user = User.register("Alice", "alice@example.com");

		assertThat(user.id()).isNull();
	}

	@Test
	void registerTruncatesCreatedAtToMicroseconds() {
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		User user = User.register("Alice", "alice@example.com");

		Instant after = Instant.now();
		assertThat(user.createdAt()).isEqualTo(user.createdAt().truncatedTo(ChronoUnit.MICROS));
		assertThat(user.createdAt()).isBetween(before, after);
	}

}
