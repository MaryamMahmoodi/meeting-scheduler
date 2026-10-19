package com.demo.meetingscheduler.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.Meeting;
import com.demo.meetingscheduler.domain.model.TimeRange;

/** Outbound port: where meetings are stored. */
public interface MeetingRepository {

	/**
	 * Saves a new meeting and writes it through immediately. Booking a slot that already has a meeting
	 * surfaces here as a {@code DomainException} (conflict, {@code SLOT_NOT_FREE}).
	 */
	Meeting save(Meeting meeting);

	boolean existsBySlotId(UUID slotId);

	/** Meetings the user organizes or participates in that intersect {@code [from, to)}, ordered by start. */
	List<Meeting> findForUserIntersecting(UUID userId, TimeRange range);

	/** The meeting, if the user organizes or participates in it. */
	Optional<Meeting> findByIdForUser(UUID meetingId, UUID userId);

}
