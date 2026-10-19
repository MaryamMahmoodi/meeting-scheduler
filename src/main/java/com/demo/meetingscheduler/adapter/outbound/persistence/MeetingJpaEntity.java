package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.Meeting;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "meetings")
class MeetingJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organizer_id", nullable = false, updatable = false)
	private UUID organizerId;

	/** The organizer's slot that was reserved for this meeting. */
	@Column(name = "slot_id", nullable = false, updatable = false)
	private UUID slotId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// Participants are plain user IDs. Batch fetching keeps list queries at two statements.
	@ElementCollection
	@CollectionTable(name = "meeting_participants", joinColumns = @JoinColumn(name = "meeting_id"))
	@Column(name = "user_id", nullable = false)
	@BatchSize(size = 100)
	private Set<UUID> participantIds = new HashSet<>();

	protected MeetingJpaEntity() {
	}

	static MeetingJpaEntity fromDomain(Meeting meeting) {
		MeetingJpaEntity entity = new MeetingJpaEntity();
		entity.organizerId = meeting.organizerId();
		entity.slotId = meeting.slotId();
		entity.title = meeting.title();
		entity.description = meeting.description();
		entity.startTime = meeting.startTime();
		entity.endTime = meeting.endTime();
		entity.participantIds = new HashSet<>(meeting.participantIds());
		entity.createdAt = meeting.createdAt();
		return entity;
	}

	/** Touches the lazy participant collection, so call it inside the transaction. */
	Meeting toDomain() {
		return new Meeting(id, organizerId, slotId, title, description, startTime, endTime, participantIds,
				createdAt);
	}

}
