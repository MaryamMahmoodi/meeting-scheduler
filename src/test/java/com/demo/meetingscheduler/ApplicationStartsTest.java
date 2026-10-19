package com.demo.meetingscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.demo.meetingscheduler.support.IntegrationTest;

/** Boots the application against PostgreSQL: Flyway migrates and Hibernate validates the schema. */
class ApplicationStartsTest extends IntegrationTest {

	@Test
	void healthIsUp() throws Exception {
		String body = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		assertThat(body).contains("\"status\":\"UP\"");
	}

	@Test
	void swaggerUiServesTheCanonicalContract() throws Exception {
		mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
	}

}
