package com.demo.meetingscheduler.application.service;

import java.util.UUID;

import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;

/** Existence checks and "not found" failures shared by the use cases. */
final class Lookups {

	private Lookups() {
	}

	static void requireUser(UserRepository users, UUID userId) {
		if (!users.existsById(userId)) {
			throw userNotFound(userId);
		}
	}

	/** Like {@link #requireUser} but also takes the per-user write lock for the rest of the transaction. */
	static void requireUserForUpdate(UserRepository users, UUID userId) {
		if (!users.lockById(userId)) {
			throw userNotFound(userId);
		}
	}

	static DomainException userNotFound(UUID userId) {
		return DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User " + userId + " was not found.");
	}

	static DomainException slotNotFound(UUID userId, UUID slotId) {
		return DomainException.notFound(ErrorCode.SLOT_NOT_FOUND,
				"Time slot " + slotId + " was not found for user " + userId + ".");
	}

}
