package com.demo.meetingscheduler.adapter.in.web.error;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.demo.meetingscheduler.domain.exception.DomainException;
import com.demo.meetingscheduler.domain.exception.FieldViolation;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central translation of failures into RFC 9457 problem details. Framework errors that are not
 * overridden here (unknown path, 405, 406, 415) keep Spring's {@code about:blank} problem type.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	ResponseEntity<Object> handleDomainException(DomainException ex, WebRequest request) {
		HttpStatus status = switch (ex.kind()) {
			case VALIDATION -> HttpStatus.BAD_REQUEST;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
		};
		ProblemDetail problem = problem(status, ProblemType.of(ex.code()), ex.getMessage());
		if (!ex.violations().isEmpty()) {
			problem.setProperty("errors", ex.violations());
		}
		return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Unexpected error", ex);
		ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemType.INTERNAL_ERROR,
				"An unexpected error occurred.");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
		String message = ex.getMostSpecificCause().getMessage();
		com.demo.meetingscheduler.domain.exception.ErrorCode errorCode;
		String detail;

		// Map database constraint errors to corresponding domain ErrorCodes
		if (message != null && (message.contains("users_email_key") || message.contains("email"))) {
			errorCode = com.demo.meetingscheduler.domain.exception.ErrorCode.EMAIL_ALREADY_REGISTERED;
			detail = "Email is already registered.";
		} else {
			errorCode = com.demo.meetingscheduler.domain.exception.ErrorCode.SLOT_OVERLAP;
			detail = "A resource conflict or overlap occurred.";
		}

		ProblemDetail problem = problem(HttpStatus.CONFLICT, ProblemType.of(errorCode), detail);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField().replaceAll("\\[\\d+]", ""), error.getDefaultMessage()))
				.toList();
		return validationProblem("The request contains invalid fields.", errors, ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		FieldViolation violation = new FieldViolation(ex.getParameterName(), "is required");
		return validationProblem("Required parameter '" + ex.getParameterName() + "' is missing.",
				List.of(violation), ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return malformed(readableDetail(ex), ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return malformed("Parameter '" + ex.getPropertyName() + "' must be " + expectedType(ex.getRequiredType()) + ".",
				ex, request);
	}

	/** Forces {@code application/problem+json} regardless of the client's Accept header. */
	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
			WebRequest request) {
		if (body instanceof ProblemDetail) {
			HttpHeaders problemHeaders = new HttpHeaders();
			problemHeaders.putAll(headers);
			problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
			return new ResponseEntity<>(body, problemHeaders, statusCode);
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private ResponseEntity<Object> validationProblem(String detail, List<FieldViolation> errors, Exception ex,
			WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemType.VALIDATION_ERROR, detail);
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
	}

	private ResponseEntity<Object> malformed(String detail, Exception ex, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemType.MALFORMED_REQUEST, detail);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
	}

	private static ProblemDetail problem(HttpStatus status, ProblemType type, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(type.uri());
		problem.setTitle(type.title());
		return problem;
	}

	private static String readableDetail(HttpMessageNotReadableException ex) {
		if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
			return "Unknown property '" + unknown.getPropertyName() + "'.";
		}
		if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
			String path = mapping.getPath().stream()
					.map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
					.collect(Collectors.joining("."));
			return "Invalid value for property '" + path + "'.";
		}
		return "The request body is missing or is not valid JSON.";
	}

	private static String expectedType(Class<?> type) {
		if (type == null) {
			return "valid";
		}
		if (UUID.class.equals(type)) {
			return "a UUID";
		}
		if (java.time.Instant.class.equals(type)) {
			return "an ISO-8601 date-time with offset";
		}
		return "of type " + type.getSimpleName();
	}

}
