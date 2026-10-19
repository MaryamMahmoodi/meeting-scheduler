package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.domain.model.TimeRange;
import com.demo.meetingscheduler.domain.model.TimeSlot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the transaction of the calling use case; entities are mapped (and lazy state read) inside it. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class TimeSlotPersistenceAdapter implements TimeSlotRepository {

	private final TimeSlotJpaRepository jpa;

	TimeSlotPersistenceAdapter(TimeSlotJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public TimeSlot save(TimeSlot slot) {
		TimeSlotJpaEntity entity = slot.id() == null ? new TimeSlotJpaEntity() : managed(slot.id());
		entity.apply(slot);
		try {
			return jpa.saveAndFlush(entity).toDomain();
		}
		catch (DataIntegrityViolationException ex) {
			throw ConstraintViolations.translate(ex);
		}
	}

	@Override
	public Optional<TimeSlot> findByIdAndUserId(UUID slotId, UUID userId) {
		return jpa.findByIdAndUserId(slotId, userId).map(TimeSlotJpaEntity::toDomain);
	}

	@Override
	public Optional<TimeSlot> lockByIdAndUserId(UUID slotId, UUID userId) {
		return jpa.findByIdAndUserIdForUpdate(slotId, userId).map(TimeSlotJpaEntity::toDomain);
	}

	@Override
	public List<TimeSlot> findIntersecting(UUID userId, TimeRange range) {
		return jpa.findIntersecting(userId, range.from(), range.to()).stream().map(TimeSlotJpaEntity::toDomain)
				.toList();
	}

	@Override
	public boolean existsOverlapping(UUID userId, Instant start, Instant end) {
		return jpa.existsByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(userId, end, start);
	}

	@Override
	public boolean existsOverlappingExcluding(UUID userId, UUID excludedSlotId, Instant start, Instant end) {
		return jpa.existsByUserIdAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(userId, excludedSlotId, end,
				start);
	}

	@Override
	public void delete(TimeSlot slot) {
		jpa.deleteById(slot.id());
	}

	private TimeSlotJpaEntity managed(UUID id) {
		return jpa.findById(id).orElseThrow(() -> new IllegalStateException("Time slot " + id + " does not exist."));
	}

}
