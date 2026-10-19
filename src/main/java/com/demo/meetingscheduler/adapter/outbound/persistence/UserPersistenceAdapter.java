package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the transaction of the calling use case; entities are mapped (and lazy state read) inside it. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class UserPersistenceAdapter implements UserRepository {

	private final UserJpaRepository jpa;

	UserPersistenceAdapter(UserJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public User save(User user) {
		try {
			return jpa.saveAndFlush(UserJpaEntity.fromDomain(user)).toDomain();
		}
		catch (DataIntegrityViolationException ex) {
			throw ConstraintViolations.translate(ex);
		}
	}

	@Override
	public Optional<User> findById(UUID userId) {
		return jpa.findById(userId).map(UserJpaEntity::toDomain);
	}

	@Override
	public boolean existsById(UUID userId) {
		return jpa.existsById(userId);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpa.existsByEmail(email);
	}

	@Override
	public boolean lockById(UUID userId) {
		return jpa.lockById(userId).isPresent();
	}

	@Override
	public Set<UUID> findExistingIds(Collection<UUID> userIds) {
		return jpa.findAllById(userIds).stream().map(UserJpaEntity::getId).collect(Collectors.toSet());
	}

}
