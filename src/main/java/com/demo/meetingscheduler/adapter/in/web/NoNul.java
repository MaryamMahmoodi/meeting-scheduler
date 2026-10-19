package com.demo.meetingscheduler.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/** PostgreSQL text columns cannot store U+0000, so it is rejected up front instead of failing with a 500. */
@Pattern(regexp = "[^\\u0000]*")
@ReportAsSingleViolation
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNul {

	String message() default "must not contain NUL characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
