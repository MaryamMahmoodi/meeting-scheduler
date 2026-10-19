package com.demo.meetingscheduler.adapter.in.web.error;

import java.net.URI;

import com.demo.meetingscheduler.domain.exception.ErrorCode;

/**
 * Problem types of the API contract (docs/openapi.yaml, ProblemDetail.type). {@code MALFORMED_REQUEST} and
 * {@code INTERNAL_ERROR} exist only at the HTTP boundary; the others stand for a domain {@link ErrorCode}.
 */
public enum ProblemType {

	VALIDATION_ERROR("validation-error", "Validation failed"),
	MALFORMED_REQUEST("malformed-request", "Malformed request"),
	USER_NOT_FOUND("user-not-found", "User not found"),
	SLOT_NOT_FOUND("slot-not-found", "Time slot not found"),
	MEETING_NOT_FOUND("meeting-not-found", "Meeting not found"),
	EMAIL_ALREADY_REGISTERED("email-already-registered", "Email already registered"),
	SLOT_OVERLAP("slot-overlap", "Time slot overlaps an existing slot"),
	SLOT_NOT_FREE("slot-not-free", "Time slot is not free"),
	SLOT_BOOKED("slot-booked", "Time slot is booked by a meeting"),
	INTERNAL_ERROR("internal-error", "Internal server error");

	private final URI uri;
	private final String title;

	ProblemType(String code, String title) {
		this.uri = URI.create("urn:mini-dood:problem:" + code);
		this.title = title;
	}

	public static ProblemType of(ErrorCode code) {
		return switch (code) {
			case VALIDATION_ERROR -> VALIDATION_ERROR;
			case USER_NOT_FOUND -> USER_NOT_FOUND;
			case SLOT_NOT_FOUND -> SLOT_NOT_FOUND;
			case MEETING_NOT_FOUND -> MEETING_NOT_FOUND;
			case EMAIL_ALREADY_REGISTERED -> EMAIL_ALREADY_REGISTERED;
			case SLOT_OVERLAP -> SLOT_OVERLAP;
			case SLOT_NOT_FREE -> SLOT_NOT_FREE;
			case SLOT_BOOKED -> SLOT_BOOKED;
		};
	}

	public URI uri() {
		return uri;
	}

	public String title() {
		return title;
	}

}
