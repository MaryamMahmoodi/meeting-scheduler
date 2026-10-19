package com.demo.meetingscheduler.application.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.demo.meetingscheduler.application.port.in.ManageMeetingsUseCase;
import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.Meeting;
import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meeting booking. Only the organizer's slot is reserved; participants are stored as user IDs and
 * their availability is neither checked nor reserved.
 */
public class MeetingService implements ManageMeetingsUseCase {

	private final MeetingRepository meetings;
	private final TimeSlotRepository slots;
	private final UserRepository users;

	public MeetingService(MeetingRepository meetings, TimeSlotRepository slots, UserRepository users) {
		this.meetings = meetings;
		this.slots = slots;
		this.users = users;
	}

	/**
	 * The slot is locked, so two concurrent bookings of the same slot are serialized and the second one sees
	 * it as BUSY. Slot update and meeting insert commit or roll back together.
	 */
	@Override
	@Transactional
	public Meeting create(UUID organizerId, CreateMeetingCommand command) {
		Set<UUID> participantIds = validatedParticipants(organizerId, command.participantIds());
		Lookups.requireUser(users, organizerId);
		TimeSlot slot = slots.lockByIdAndUserId(command.slotId(), organizerId)
				.orElseThrow(() -> Lookups.slotNotFound(organizerId, command.slotId()));
		requireParticipantsExist(participantIds);
		TimeSlot booked = slots.save(slot.book());
		return meetings.save(Meeting.book(organizerId, booked, command.title(), command.description(),
				participantIds));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Meeting> list(UUID userId, Instant from, Instant to) {
		TimeRange range = TimeRange.of(from, to);
		Lookups.requireUser(users, userId);
		return meetings.findForUserIntersecting(userId, range);
	}

	@Override
	@Transactional(readOnly = true)
	public Meeting get(UUID userId, UUID meetingId) {
		Lookups.requireUser(users, userId);
		return meetings.findByIdForUser(meetingId, userId)
				.orElseThrow(() -> DomainException.notFound(ErrorCode.MEETING_NOT_FOUND,
						"Meeting " + meetingId + " was not found for user " + userId + "."));
	}

	private static Set<UUID> validatedParticipants(UUID organizerId, List<UUID> participantIds) {
		if (participantIds == null) {
			return Set.of();
		}
		Set<UUID> unique = new HashSet<>(participantIds);
		if (unique.size() != participantIds.size()) {
			throw DomainException.validation("participantIds", "must not contain duplicates");
		}
		if (unique.contains(organizerId)) {
			throw DomainException.validation("participantIds", "must not contain the organizer");
		}
		return unique;
	}

	private void requireParticipantsExist(Set<UUID> participantIds) {
		if (participantIds.isEmpty()) {
			return;
		}
		Set<UUID> missing = new TreeSet<>(participantIds);
		missing.removeAll(users.findExistingIds(participantIds));
		if (!missing.isEmpty()) {
			String ids = missing.stream().map(UUID::toString).sorted().collect(Collectors.joining(", "));
			throw DomainException.notFound(ErrorCode.USER_NOT_FOUND, "Participant user(s) not found - " + ids);
		}
	}

}
