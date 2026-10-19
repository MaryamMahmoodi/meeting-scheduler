package com.demo.meetingscheduler.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.demo.meetingscheduler.support.IntegrationTest;

/** Lenient framework defaults (coercion, short UUIDs, epoch timestamps) must not leak through the contract. */
class StrictInputTest extends IntegrationTest {

	private static final String MALFORMED = "urn:mini-dood:problem:malformed-request";

	@ParameterizedTest
	@ValueSource(strings = { "1700000000", "Mon, 02 Nov 2026 10:00:00 GMT", "2026-11-02T10:00:00", "2026-11-02" })
	void queryTimestampsMustBeIsoDateTimesWithOffset(String from) throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user + "/availability").param("from", from).param("to",
				"2030-01-01T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(MALFORMED));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1-1-1-1-1", "not-a-uuid", "00000000-0000-0000-0000-00000000000g" })
	void pathUuidsMustBeCanonical(String id) throws Exception {
		mockMvc.perform(get("/api/v1/users/" + id)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(MALFORMED));
	}

	@Test
	void canonicalUuidsInEitherCaseStillWork() throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user.toString().toUpperCase())).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"startTime\":\"\",\"endTime\":\"2030-01-01T10:00:00Z\"}",
			"{\"startTime\":\"1700000000\",\"endTime\":\"2030-01-01T10:00:00Z\"}",
			"{\"startTime\":1700000000,\"endTime\":\"2030-01-01T10:00:00Z\"}",
			"{\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\",\"status\":0}",
			"{\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\"} trailing"
	})
	void slotBodiesWithWronglyTypedValuesAreMalformed(String body) throws Exception {
		UUID user = api().createUser();

		api().postJson("/api/v1/users/" + user + "/slots", body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(MALFORMED));
	}

	@Test
	void emptyStringSlotIdIsMalformedNotMissing() throws Exception {
		UUID user = api().createUser();

		api().postJson("/api/v1/users/" + user + "/meetings", "{\"slotId\":\"\",\"title\":\"t\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value(MALFORMED));
	}

}
