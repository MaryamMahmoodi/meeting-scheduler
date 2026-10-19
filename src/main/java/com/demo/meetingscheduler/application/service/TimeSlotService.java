package com.demo.meetingscheduler.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase;
import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.model.SlotInterval;
import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time slot rules. Check order follows the contract: validation (400), existence (404), conflicts (409).
 * A slot is always looked up through its owner, so another user's slot is simply "not found".
 */
public class TimeSlotService implements ManageTimeSlotsUseCase {

	private final TimeSlotRepository slots;
	private final MeetingRepository meetings;
	private final UserRepository users;

	public TimeSlotService(TimeSlotRepository slots, MeetingRepository meetings, UserRepository users) {
		this.slots = slots;
		this.meetings = meetings;
		this.users = users;
	}

	@Override
	@Transactional
	public TimeSlot create(UUID userId, CreateTimeSlotCommand command) {
		SlotInterval interval = SlotInterval.of(command.startTime(), command.endTime());
		Lookups.requireUserForUpdate(users, userId);
		if (slots.existsOverlapping(userId, interval.start(), interval.end())) {
			throw overlap();
		}
		// Writers of one user are serialized by the user lock above, so the pre-check is authoritative; the
		// exclusion constraint is only a backstop, and the repository writes through so it fires here (409).
		return slots.save(TimeSlot.create(userId, interval, command.status()));
	}

	@Override
	@Transactional(readOnly = true)
	public TimeSlot get(UUID userId, UUID slotId) {
		Lookups.requireUser(users, userId);
		return slots.findByIdAndUserId(slotId, userId).orElseThrow(() -> Lookups.slotNotFound(userId, slotId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TimeSlot> list(UUID userId, Instant from, Instant to) {
		TimeRange range = TimeRange.of(from, to);
		Lookups.requireUser(users, userId);
		return slots.findIntersecting(userId, range);
	}

	@Override
	@Transactional
	public TimeSlot update(UUID userId, UUID slotId, UpdateTimeSlotCommand command) {
		SlotInterval interval = SlotInterval.of(command.startTime(), command.endTime());
		Lookups.requireUserForUpdate(users, userId);
		TimeSlot slot = lockOwnedSlot(userId, slotId);
		requireNotBooked(slot);
		if (slots.existsOverlappingExcluding(userId, slotId, interval.start(), interval.end())) {
			throw overlap();
		}
		TimeSlot updated = slot.reschedule(interval);
		if (command.status() != null) {
			updated = updated.withStatus(command.status());
		}
		return slots.save(updated);
	}

	@Override
	@Transactional
	public TimeSlot updateAvailability(UUID userId, UUID slotId, TimeSlotStatus status) {
		Lookups.requireUser(users, userId);
		TimeSlot slot = lockOwnedSlot(userId, slotId);
		requireNotBooked(slot);
		return slots.save(slot.withStatus(status));
	}

	@Override
	@Transactional
	public void delete(UUID userId, UUID slotId) {
		Lookups.requireUser(users, userId);
		TimeSlot slot = lockOwnedSlot(userId, slotId);
		requireNotBooked(slot);
		slots.delete(slot);
	}

	private TimeSlot lockOwnedSlot(UUID userId, UUID slotId) {
		return slots.lockByIdAndUserId(slotId, userId).orElseThrow(() -> Lookups.slotNotFound(userId, slotId));
	}

	private void requireNotBooked(TimeSlot slot) {
		if (meetings.existsBySlotId(slot.id())) {
			throw DomainException.conflict(ErrorCode.SLOT_BOOKED, "The slot is booked by a meeting and cannot be changed.");
		}
	}

	private static DomainException overlap() {
		return DomainException.conflict(ErrorCode.SLOT_OVERLAP,
				"The requested time range overlaps an existing slot of this user.");
	}

}
