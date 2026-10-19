package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MeetingJpaRepository extends JpaRepository<MeetingJpaEntity, UUID> {

	boolean existsBySlotId(UUID slotId);

	/** Meetings the user organizes or participates in that intersect {@code [from, to)}. */
	@Query("""
			select m from MeetingJpaEntity m
			where m.startTime < :to and m.endTime > :from
			  and (m.organizerId = :userId or :userId member of m.participantIds)
			order by m.startTime, m.endTime, m.id""")
	List<MeetingJpaEntity> findForUserIntersecting(@Param("userId") UUID userId, @Param("from") Instant from,
			@Param("to") Instant to);

	@Query("""
			select m from MeetingJpaEntity m
			where m.id = :id and (m.organizerId = :userId or :userId member of m.participantIds)""")
	Optional<MeetingJpaEntity> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

}
