package com.demo.meetingscheduler.adapter.in.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.ErrorCode;
import com.demo.meetingscheduler.domain.exception.FieldViolation;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/** Calls the handler directly (no Spring context); the handler methods are package-private. */
class ApiExceptionHandlerTest {

	private static final String SECRET = "jdbc:postgresql://secret-host/db password=hunter2";

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	private static ServletWebRequest request() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/v1/users");
		return new ServletWebRequest(servletRequest);
	}

	private static ProblemDetail problemOf(ResponseEntity<Object> response) {
		assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
		return (ProblemDetail) response.getBody();
	}

	private static void assertProblemJson(ResponseEntity<Object> response) {
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(SECRET, new SQLException(SECRET),
				constraintName);
		return new DataIntegrityViolationException(SECRET, cause);
	}

	@Test
	void notFoundDomainExceptionBecomesProblemWithTypeStatusAndNoErrors() {
		DomainException ex = DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User x was not found.");

		ResponseEntity<Object> response = handler.handleDomainException(ex, request());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertProblemJson(response);
		ProblemDetail problem = problemOf(response);
		assertThat(problem.getType()).isEqualTo(ProblemType.USER_NOT_FOUND.uri());
		assertThat(problem.getType().toString()).isEqualTo("urn:mini-dood:problem:user-not-found");
		assertThat(problem.getTitle()).isEqualTo(ProblemType.USER_NOT_FOUND.title());
		assertThat(problem.getStatus()).isEqualTo(404);
		assertThat(problem.getDetail()).isEqualTo("User x was not found.");
		// "instance" is filled in later by Spring MVC's return value handler, so it is covered by the integration tests.
		assertThat(problem.getProperties()).isNull();
	}

	@Test
	void conflictDomainExceptionBecomes409Problem() {
		DomainException ex = DomainException.conflict(ErrorCode.SLOT_BOOKED, "booked");

		ResponseEntity<Object> response = handler.handleDomainException(ex, request());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertProblemJson(response);
		ProblemDetail problem = problemOf(response);
		assertThat(problem.getType().toString()).isEqualTo("urn:mini-dood:problem:slot-booked");
		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).isEqualTo("booked");
		assertThat(problem.getProperties()).isNull();
	}

	@Test
	void validationDomainExceptionBecomes400ProblemWithErrorsArray() {
		DomainException ex = DomainException.validation("endTime", "must be after startTime");

		ResponseEntity<Object> response = handler.handleDomainException(ex, request());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertProblemJson(response);
		ProblemDetail problem = problemOf(response);
		assertThat(problem.getType().toString()).isEqualTo("urn:mini-dood:problem:validation-error");
		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getProperties()).containsKey("errors");
		assertThat(problem.getProperties().get("errors"))
				.isEqualTo(List.of(new FieldViolation("endTime", "must be after startTime")));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"USER_NOT_FOUND, user-not-found",
			"SLOT_NOT_FOUND, slot-not-found",
			"MEETING_NOT_FOUND, meeting-not-found"
	})
	void notFoundDomainExceptionIsReportedWithTheProblemTypeOfItsCode(ErrorCode code, String problemCode) {
		ResponseEntity<Object> response = handler.handleDomainException(DomainException.notFound(code, "gone"),
				request());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(problemOf(response).getType().toString()).isEqualTo("urn:mini-dood:problem:" + problemCode);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"EMAIL_ALREADY_REGISTERED, email-already-registered",
			"SLOT_OVERLAP, slot-overlap",
			"SLOT_NOT_FREE, slot-not-free",
			"SLOT_BOOKED, slot-booked"
	})
	void conflictDomainExceptionIsReportedWithTheProblemTypeOfItsCode(ErrorCode code, String problemCode) {
		ResponseEntity<Object> response = handler.handleDomainException(DomainException.conflict(code, "clash"),
				request());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertProblemJson(response);
		ProblemDetail problem = problemOf(response);
		assertThat(problem.getType().toString()).isEqualTo("urn:mini-dood:problem:" + problemCode);
		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).isEqualTo("clash");
	}

	@Test
	void problemTypeOfCoversEveryErrorCodeWithADistinctKebabCaseUrn() {
		Set<ProblemType> seen = new HashSet<>();
		for (ErrorCode code : ErrorCode.values()) {
			ProblemType type = ProblemType.of(code);

			assertThat(type).as("problem type of %s", code).isNotNull();
			assertThat(type.uri().toString()).isEqualTo(
					"urn:mini-dood:problem:" + code.name().toLowerCase().replace('_', '-'));
			assertThat(type.title()).isNotBlank();
			assertThat(seen.add(type)).as("problem type of %s is distinct", code).isTrue();
		}
		assertThat(seen).hasSize(ErrorCode.values().length);
		assertThat(Arrays.stream(ProblemType.values()).map(ProblemType::uri).distinct().count())
				.isEqualTo(ProblemType.values().length);
	}

	@Test
	void dataIntegrityViolationReachingTheHandlerUnmappedBecomesInternalErrorWithoutLeakingTheMessage() {
		ResponseEntity<Object> response = handler.handleUnexpected(violation("uq_users_email"), request());

		assertInternalErrorWithoutLeak(response);
	}

	@Test
	void unknownConstraintBecomesInternalErrorWithoutLeakingTheMessage() {
		ResponseEntity<Object> response = handler.handleUnexpected(violation("some_other_constraint"), request());

		assertInternalErrorWithoutLeak(response);
	}

	@Test
	void dataIntegrityViolationWithoutConstraintNameBecomesInternalError() {
		ResponseEntity<Object> response = handler
				.handleUnexpected(new DataIntegrityViolationException(SECRET, new SQLException(SECRET)), request());

		assertInternalErrorWithoutLeak(response);
	}

	@Test
	void plainExceptionBecomesInternalErrorWithoutLeakingTheMessage() {
		ResponseEntity<Object> response = handler.handleUnexpected(new IllegalStateException(SECRET), request());

		assertInternalErrorWithoutLeak(response);
	}

	private static void assertInternalErrorWithoutLeak(ResponseEntity<Object> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertProblemJson(response);
		ProblemDetail problem = problemOf(response);
		assertThat(problem.getType().toString()).isEqualTo("urn:mini-dood:problem:internal-error");
		assertThat(problem.getTitle()).isEqualTo(ProblemType.INTERNAL_ERROR.title());
		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).isNotBlank().doesNotContain("hunter2").doesNotContain("secret-host");
		assertThat(problem.getTitle()).doesNotContain("hunter2");
		assertThat(problem.getProperties()).isNull();
	}

}
