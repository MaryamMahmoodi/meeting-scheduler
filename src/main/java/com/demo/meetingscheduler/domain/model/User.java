package com.demo.meetingscheduler.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A user. {@code id} is {@code null} until the user has been saved. */
public record User(UUID id, String name, String email, Instant createdAt) {

	/** A new, not yet saved user. Emails are case-insensitive, so they are kept lower-cased. */
	public static User register(String name, String email) {
		return new User(null, name, email.toLowerCase(Locale.ROOT), Timestamps.now());
	}

}
