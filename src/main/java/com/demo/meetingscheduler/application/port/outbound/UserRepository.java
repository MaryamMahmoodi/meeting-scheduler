package com.demo.meetingscheduler.application.port.outbound;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.User;

/** Outbound port: where users are stored. */
public interface UserRepository {

	/**
	 * Saves a new user and writes it through immediately. A concurrent duplicate email surfaces here as a
	 * {@code DomainException} (conflict, {@code EMAIL_ALREADY_REGISTERED}).
	 */
	User save(User user);

	Optional<User> findById(UUID userId);

	boolean existsById(UUID userId);

	boolean existsByEmail(String email);

	/**
	 * Takes a write lock on the user for the rest of the transaction and returns whether the user exists.
	 * It serializes writers of the same user's calendar, so two concurrent overlap checks cannot both pass.
	 */
	boolean lockById(UUID userId);

	/** The subset of {@code userIds} that belongs to existing users. */
	Set<UUID> findExistingIds(Collection<UUID> userIds);

}
