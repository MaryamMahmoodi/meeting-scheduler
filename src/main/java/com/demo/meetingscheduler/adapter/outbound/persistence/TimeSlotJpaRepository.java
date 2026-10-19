package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TimeSlotJpaRepository extends JpaRepository<TimeSlotJpaEntity, UUID> {

	Optional<TimeSlotJpaEntity> findByIdAndUserId(UUID id, UUID userId);

	/** Row lock ({@code SELECT ... FOR UPDATE}) so concurrent bookings and edits of a slot are serialized. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from TimeSlotJpaEntity s where s.id = :id and s.userId = :userId")
	Optional<TimeSlotJpaEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

	/** Slots that intersect {@code [from, to)}: {@code start < to AND end > from}. */
	@Query("""
			select s from TimeSlotJpaEntity s
			where s.userId = :userId and s.startTime < :to and s.endTime > :from
			order by s.startTime""")
	List<TimeSlotJpaEntity> findIntersecting(@Param("userId") UUID userId, @Param("from") Instant from,
			@Param("to") Instant to);

	/** Overlap pre-check: {@code existing.start < end AND existing.end > start}. */
	boolean existsByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(UUID userId, Instant end, Instant start);

	boolean existsByUserIdAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(UUID userId, UUID excludedId,
			Instant end, Instant start);

}
