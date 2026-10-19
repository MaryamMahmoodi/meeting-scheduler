package com.demo.meetingscheduler.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin helpers that drive the HTTP API in tests, so test bodies read as scenarios. */
public class Api {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	Api(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	/** Creates a user with a unique email and returns its id. */
	public UUID createUser() throws Exception {
		return createUser("User " + UUID.randomUUID());
	}

	public UUID createUser(String name) throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		return id(postJson("/api/v1/users", "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}")
				.andExpect(status().isCreated()));
	}

	/** Creates a FREE slot and returns its id. Times are ISO-8601 strings such as {@code 2030-01-01T09:00:00Z}. */
	public UUID createSlot(UUID userId, String start, String end) throws Exception {
		return id(postJson("/api/v1/users/" + userId + "/slots", slotBody(start, end))
				.andExpect(status().isCreated()));
	}

	public ResultActions postSlot(UUID userId, String start, String end) throws Exception {
		return postJson("/api/v1/users/" + userId + "/slots", slotBody(start, end));
	}

	public ResultActions patchStatus(UUID userId, UUID slotId, String status) throws Exception {
		return mockMvc.perform(patch("/api/v1/users/" + userId + "/slots/" + slotId + "/availability")
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	/** Books a meeting and returns the raw result so tests can assert on success or failure. */
	public ResultActions postMeeting(UUID organizerId, UUID slotId, String title, UUID... participantIds)
			throws Exception {
		StringBuilder participants = new StringBuilder();
		for (UUID participant : participantIds) {
			participants.append(participants.isEmpty() ? "" : ",").append('"').append(participant).append('"');
		}
		return postJson("/api/v1/users/" + organizerId + "/meetings", "{\"slotId\":\"" + slotId + "\",\"title\":\""
				+ title + "\",\"participantIds\":[" + participants + "]}");
	}

	public ResultActions getAvailability(UUID userId, String from, String to) throws Exception {
		return mockMvc.perform(get("/api/v1/users/" + userId + "/availability").param("from", from).param("to", to));
	}

	public ResultActions postJson(String path, String json) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	public UUID id(ResultActions result) throws Exception {
		return UUID.fromString(json(result).get("id").asText());
	}

	public JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	public static String slotBody(String start, String end) {
		return "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
	}

}
