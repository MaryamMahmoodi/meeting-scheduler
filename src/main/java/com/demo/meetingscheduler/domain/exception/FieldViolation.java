package com.demo.meetingscheduler.domain.exception;

/** One invalid field of a {@link DomainException} of kind {@link DomainException.Kind#VALIDATION}. */
public record FieldViolation(String field, String message) {
}
