package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.domain.model.Meeting;
import com.demo.meetingscheduler.domain.model.TimeRange;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the transaction of the calling use case; entities are mapped (and lazy state read) inside it. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class MeetingPersistenceAdapter implements MeetingRepository {

	private final MeetingJpaRepository jpa;

	MeetingPersistenceAdapter(MeetingJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Meeting save(Meeting meeting) {
		try {
			return jpa.saveAndFlush(MeetingJpaEntity.fromDomain(meeting)).toDomain();
		}
		catch (DataIntegrityViolationException ex) {
			throw ConstraintViolations.translate(ex);
		}
	}

	@Override
	public boolean existsBySlotId(UUID slotId) {
		return jpa.existsBySlotId(slotId);
	}

	@Override
	public List<Meeting> findForUserIntersecting(UUID userId, TimeRange range) {
		return jpa.findForUserIntersecting(userId, range.from(), range.to()).stream()
				.map(MeetingJpaEntity::toDomain).toList();
	}

	@Override
	public Optional<Meeting> findByIdForUser(UUID meetingId, UUID userId) {
		return jpa.findByIdForUser(meetingId, userId).map(MeetingJpaEntity::toDomain);
	}

}
