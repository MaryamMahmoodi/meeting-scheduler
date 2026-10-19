package com.demo.meetingscheduler.adapter.in.web.strict;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * {@link UUID#fromString} also accepts short forms such as {@code 1-1-1-1-1}. Path and query UUIDs must be
 * canonical (8-4-4-4-12 hex digits); anything else is a 400 {@code malformed-request}.
 *
 * <p>This is a property editor rather than a converter on purpose: when a converter fails, Spring falls
 * back to its lenient default {@code UUIDEditor}, whereas a custom editor takes precedence.
 */
@ControllerAdvice
class StrictUuidBinding {

	private static final Pattern CANONICAL_UUID = Pattern
			.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	@InitBinder
	void registerStrictUuidEditor(WebDataBinder binder) {
		binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (text == null || !CANONICAL_UUID.matcher(text).matches()) {
					throw new IllegalArgumentException("Not a UUID");
				}
				setValue(UUID.fromString(text));
			}
		});
	}

}
