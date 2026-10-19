package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.TimeSlot;
import com.demo.meetingscheduler.domain.model.TimeSlotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "time_slots")
class TimeSlotJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TimeSlotStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TimeSlotJpaEntity() {
	}

	/** Copies the state of the domain slot onto this (new or managed) entity. */
	void apply(TimeSlot slot) {
		this.userId = slot.userId();
		this.startTime = slot.startTime();
		this.endTime = slot.endTime();
		this.status = slot.status();
		this.createdAt = slot.createdAt();
	}

	TimeSlot toDomain() {
		return new TimeSlot(id, userId, startTime, endTime, status, createdAt);
	}

}
