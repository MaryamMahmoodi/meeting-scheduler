package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;

import com.demo.meetingscheduler.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
class UserJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserJpaEntity() {
	}

	static UserJpaEntity fromDomain(User user) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.name = user.name();
		entity.email = user.email();
		entity.createdAt = user.createdAt();
		return entity;
	}

	UUID getId() {
		return id;
	}

	User toDomain() {
		return new User(id, name, email, createdAt);
	}

}
