package com.demo.meetingscheduler.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.dao.DataIntegrityViolationException;

/** Plain unit test (no Spring context); {@code ConstraintViolations} is package-private. */
class ConstraintViolationsTest {

	private static final String SECRET = "jdbc:postgresql://secret-host/db password=hunter2";

	private static DataIntegrityViolationException violation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(SECRET, new SQLException(SECRET),
				constraintName);
		return new DataIntegrityViolationException(SECRET, cause);
	}

	private static DomainException conflictOf(RuntimeException translated) {
		assertThat(translated).isInstanceOf(DomainException.class);
		DomainException conflict = (DomainException) translated;
		assertThat(conflict.kind()).isEqualTo(DomainException.Kind.CONFLICT);
		assertThat(conflict.getMessage()).isNotBlank().doesNotContain("hunter2").doesNotContain("secret-host");
		return conflict;
	}

	/**
	 * This is the cause chain a real overlap race produces: Hibernate cannot name an exclusion constraint
	 * (constraint name null), so the translation has to recognise PostgreSQL's SQLState 23P01.
	 */
	@Test
	void exclusionViolationWithoutConstraintNameBecomesSlotOverlap() {
		SQLException postgres = new SQLException("conflicting key value violates exclusion constraint", "23P01");
		ConstraintViolationException hibernate = new ConstraintViolationException(SECRET, postgres, null);

		RuntimeException translated = ConstraintViolations
				.translate(new DataIntegrityViolationException(SECRET, hibernate));

		assertThat(conflictOf(translated).code()).isEqualTo(ErrorCode.SLOT_OVERLAP);
	}

	@ParameterizedTest(name = "constraint {0} -> {1}")
	@CsvSource({
			"uq_users_email, EMAIL_ALREADY_REGISTERED",
			"uq_meetings_slot, SLOT_NOT_FREE"
	})
	void knownConstraintViolationBecomesConflictWithMatchingCode(String constraint, ErrorCode code) {
		RuntimeException translated = ConstraintViolations.translate(violation(constraint));

		assertThat(conflictOf(translated).code()).isEqualTo(code);
	}

	@Test
	void constraintNameWithSchemaPrefixIsStillRecognised() {
		RuntimeException translated = ConstraintViolations.translate(violation("public.uq_users_email"));

		assertThat(conflictOf(translated).code()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
	}

	@Test
	void constraintViolationNestedDeeperInTheCauseChainIsFound() {
		ConstraintViolationException hibernate = new ConstraintViolationException("x", new SQLException("x"),
				"uq_meetings_slot");
		DataIntegrityViolationException ex = new DataIntegrityViolationException("outer",
				new RuntimeException("wrapper", hibernate));

		RuntimeException translated = ConstraintViolations.translate(ex);

		assertThat(conflictOf(translated).code()).isEqualTo(ErrorCode.SLOT_NOT_FREE);
	}

	@Test
	void unknownConstraintIsReturnedUntranslated() {
		DataIntegrityViolationException ex = violation("some_other_constraint");

		assertThat(ConstraintViolations.translate(ex)).isSameAs(ex);
	}

	@Test
	void dataIntegrityViolationWithoutConstraintNameIsReturnedUntranslated() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException(SECRET, new SQLException(SECRET));

		assertThat(ConstraintViolations.translate(ex)).isSameAs(ex);
	}

}
