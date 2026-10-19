package com.demo.meetingscheduler.domain.exception;

/** What went wrong, in domain terms. The web adapter maps each code to a problem type of the API contract. */
public enum ErrorCode {
	VALIDATION_ERROR,
	USER_NOT_FOUND,
	SLOT_NOT_FOUND,
	MEETING_NOT_FOUND,
	EMAIL_ALREADY_REGISTERED,
	SLOT_OVERLAP,
	SLOT_NOT_FREE,
	SLOT_BOOKED
}
