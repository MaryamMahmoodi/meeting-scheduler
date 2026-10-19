package com.demo.meetingscheduler.adapter.outbound.persistence;

import java.sql.SQLException;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Turns the database constraints that back the business rules into domain conflicts. Races that slip past the
 * service pre-checks end up here, so the rest of the application never sees SQL states or constraint names.
 */
final class ConstraintViolations {

	private static final String EXCLUSION_VIOLATION = "23P01";

	private ConstraintViolations() {
	}

	/**
	 * The conflict the violation stands for, or the violation itself if it is not one of the known constraints
	 * (the caller rethrows it and it is reported as an internal error).
	 */
	static RuntimeException translate(DataIntegrityViolationException ex) {
		// Hibernate reports no constraint name for exclusion violations, so go by SQLState.
		// ex_time_slots_no_overlap is the only exclusion constraint in the schema.
		if (hasSqlState(ex, EXCLUSION_VIOLATION)) {
			return DomainException.conflict(ErrorCode.SLOT_OVERLAP,
					"The requested time range overlaps an existing slot of this user.");
		}
		String constraint = constraintName(ex);
		if (constraint != null) {
			if (constraint.contains("uq_users_email")) {
				return DomainException.conflict(ErrorCode.EMAIL_ALREADY_REGISTERED,
						"A user with this email already exists.");
			}
			if (constraint.contains("uq_meetings_slot")) {
				return DomainException.conflict(ErrorCode.SLOT_NOT_FREE, "The time slot has already been booked.");
			}
		}
		return ex;
	}

	private static boolean hasSqlState(Throwable ex, String sqlState) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	private static String constraintName(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

}
