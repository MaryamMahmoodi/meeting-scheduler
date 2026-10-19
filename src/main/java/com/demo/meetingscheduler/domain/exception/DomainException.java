package com.demo.meetingscheduler.domain.exception;

import java.util.List;

/**
 * A business-rule or validation failure. It carries no HTTP or database concepts: the {@link Kind} says what
 * sort of failure it is and the {@link ErrorCode} which one. Adapters decide how to report it.
 */
public class DomainException extends RuntimeException {

	public enum Kind {
		/** The input is invalid. */
		VALIDATION,
		/** A referenced entity does not exist. */
		NOT_FOUND,
		/** The request is valid but clashes with the current state. */
		CONFLICT
	}

	private final Kind kind;
	private final ErrorCode code;
	private final List<FieldViolation> violations;

	private DomainException(Kind kind, ErrorCode code, String detail, List<FieldViolation> violations) {
		super(detail);
		this.kind = kind;
		this.code = code;
		this.violations = violations;
	}

	public static DomainException notFound(ErrorCode code, String detail) {
		return new DomainException(Kind.NOT_FOUND, code, detail, List.of());
	}

	public static DomainException conflict(ErrorCode code, String detail) {
		return new DomainException(Kind.CONFLICT, code, detail, List.of());
	}

	public static DomainException validation(String field, String message) {
		return new DomainException(Kind.VALIDATION, ErrorCode.VALIDATION_ERROR, message,
				List.of(new FieldViolation(field, message)));
	}

	public Kind kind() {
		return kind;
	}

	public ErrorCode code() {
		return code;
	}

	public List<FieldViolation> violations() {
		return violations;
	}

}
