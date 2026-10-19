package com.demo.meetingscheduler.adapter.in.web.strict;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Strict conversion of query values. Spring's default accepts epoch seconds and RFC 1123 dates for
 * {@link Instant}; here only ISO-8601 date-times with an offset pass. A failed conversion becomes a 400
 * {@code malformed-request}. (UUIDs are handled by {@link StrictUuidBinding}.)
 */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(String.class, Instant.class, text -> {
			try {
				return Instant.parse(text);
			}
			catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("Not an ISO-8601 date-time with offset", ex);
			}
		});
	}

}
