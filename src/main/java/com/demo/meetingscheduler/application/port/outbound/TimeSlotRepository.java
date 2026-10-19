package com.demo.meetingscheduler.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;

/** Outbound port: where time slots are stored. */
public interface TimeSlotRepository {

	/**
	 * Inserts the slot (no id yet) or updates the existing one, and writes it through immediately. An
	 * overlap that slipped past the pre-check surfaces here as a {@code DomainException} (conflict,
	 * {@code SLOT_OVERLAP}).
	 */
	TimeSlot save(TimeSlot slot);

	Optional<TimeSlot> findByIdAndUserId(UUID slotId, UUID userId);

	/** Like {@link #findByIdAndUserId} but locks the slot for the rest of the transaction. */
	Optional<TimeSlot> lockByIdAndUserId(UUID slotId, UUID userId);

	/** The user's slots that intersect {@code [from, to)}, ordered by start. */
	List<TimeSlot> findIntersecting(UUID userId, TimeRange range);

	/** Whether a slot of the user intersects {@code [start, end)}. */
	boolean existsOverlapping(UUID userId, Instant start, Instant end);

	/** Like {@link #existsOverlapping} but ignores the slot {@code excludedSlotId}. */
	boolean existsOverlappingExcluding(UUID userId, UUID excludedSlotId, Instant start, Instant end);

	void delete(TimeSlot slot);

}
