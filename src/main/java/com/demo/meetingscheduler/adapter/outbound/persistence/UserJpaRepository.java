package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

	boolean existsByEmail(String email);

	/**
	 * Locks the user row with {@code FOR NO KEY UPDATE} and returns its id (empty if the user does not exist).
	 * That mode serializes writers of the same user's calendar but, unlike {@code FOR UPDATE}, does not
	 * conflict with the {@code KEY SHARE} locks that foreign-key checks of slot and meeting inserts take.
	 */
	@Query(value = "select id from users where id = :id for no key update", nativeQuery = true)
	Optional<UUID> lockById(@Param("id") UUID id);

}
