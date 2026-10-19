package com.demo.meetingscheduler.adapter.in.web.strict;

import java.util.UUID;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The contract is strict about JSON types: text must be a JSON string (see also {@link StrictInstantDeserializer}). */
@Configuration(proxyBeanMethods = false)
class JacksonConfig {

	@Bean
	Jackson2ObjectMapperBuilderCustomizer strictScalarTypes() {
		return builder -> builder.postConfigurer(mapper -> {
			// "" is not a UUID (by default it is silently turned into null)
			mapper.coercionConfigFor(UUID.class).setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
			mapper.coercionConfigFor(LogicalType.Textual)
					.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
		});
	}

}
