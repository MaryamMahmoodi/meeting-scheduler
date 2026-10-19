package com.demo.meetingscheduler.adapter.in.web.strict;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.springframework.boot.jackson.JsonComponent;

/**
 * Timestamps must be ISO-8601 strings with an offset. Unlike the default deserializer this rejects
 * JSON numbers (epoch values) instead of silently accepting them.
 */
@JsonComponent
class StrictInstantDeserializer extends JsonDeserializer<Instant> {

	@Override
	public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (!parser.hasToken(JsonToken.VALUE_STRING)) {
			return (Instant) context.handleUnexpectedToken(Instant.class, parser);
		}
		String text = parser.getText();
		try {
			return Instant.parse(text);
		}
		catch (DateTimeParseException ex) {
			return (Instant) context.handleWeirdStringValue(Instant.class, text,
					"must be an ISO-8601 date-time with offset");
		}
	}

}
