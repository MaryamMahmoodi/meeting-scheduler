package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * End-to-end check of the RFC 9457 error contract (docs/openapi.yaml, "Conventions" and ProblemDetail):
 * every problem type that the API can trigger, the framework errors and a robustness sweep that makes
 * sure hostile input is answered with a 4xx problem and never with a 500.
 */
class ErrorResponsesTest extends IntegrationTest {

	private static final String PREFIX = "urn:mini-dood:problem:";
	private static final String START = "2030-01-01T09:00:00Z";
	private static final String END = "2030-01-01T10:00:00Z";
	private static final String MIN_INSTANT = "-1000000000-01-01T00:00:00Z";
	private static final String MAX_INSTANT = "+1000000000-12-31T23:59:59Z";

	// ---------------------------------------------------------------- domain problem types

	@Test
	void validationErrorCarriesStatusTypeInstanceAndFieldErrors() throws Exception {
		String path = "/api/v1/users";

		JsonNode problem = assertProblem(api().postJson(path, "{\"name\":\"\",\"email\":\"not-an-email\"}"), 400,
				PREFIX + "validation-error", path);

		assertThat(problem.get("errors").isArray()).isTrue();
		assertThat(problem.get("errors")).hasSize(2);
		assertThat(fieldsOf(problem)).containsExactlyInAnyOrder("name", "email");
	}

	@Test
	void validationErrorForEndTimeNotAfterStartTimeNamesTheEndTimeField() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/slots";

		JsonNode problem = assertProblem(api().postSlot(user, END, START), 400, PREFIX + "validation-error", path);

		assertThat(fieldsOf(problem)).containsExactly("endTime");
	}

	@Test
	void validationErrorForMissingRequiredQueryParameterNamesTheParameter() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/availability";

		JsonNode problem = assertProblem(mockMvc.perform(get(path).param("to", END)), 400,
				PREFIX + "validation-error", path);

		assertThat(fieldsOf(problem)).containsExactly("from");
	}

	@Test
	void validationErrorForFromNotBeforeToNamesTheToField() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/slots";

		JsonNode problem = assertProblem(mockMvc.perform(get(path).param("from", END).param("to", START)), 400,
				PREFIX + "validation-error", path);

		assertThat(fieldsOf(problem)).containsExactly("to");
	}

	@Test
	void malformedRequestForInvalidJsonHasNoErrorsArray() throws Exception {
		String path = "/api/v1/users";

		assertProblem(api().postJson(path, "{\"name\": "), 400, PREFIX + "malformed-request", path);
	}

	@Test
	void malformedRequestForMalformedUuidInPath() throws Exception {
		String path = "/api/v1/users/not-a-uuid";

		assertProblem(mockMvc.perform(get(path)), 400, PREFIX + "malformed-request", path);
	}

	@Test
	void malformedRequestForMalformedTimestampInQuery() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/availability";

		assertProblem(mockMvc.perform(get(path).param("from", "tomorrow").param("to", END)), 400,
				PREFIX + "malformed-request", path);
	}

	@Test
	void userNotFound() throws Exception {
		String path = "/api/v1/users/" + UUID.randomUUID();

		assertProblem(mockMvc.perform(get(path)), 404, PREFIX + "user-not-found", path);
	}

	@Test
	void slotNotFound() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/slots/" + UUID.randomUUID();

		assertProblem(mockMvc.perform(get(path)), 404, PREFIX + "slot-not-found", path);
	}

	@Test
	void meetingNotFound() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/meetings/" + UUID.randomUUID();

		assertProblem(mockMvc.perform(get(path)), 404, PREFIX + "meeting-not-found", path);
	}

	@Test
	void emailAlreadyRegistered() throws Exception {
		String email = "dup-" + UUID.randomUUID() + "@example.com";
		String body = "{\"name\":\"Dup\",\"email\":\"" + email + "\"}";
		api().postJson("/api/v1/users", body).andExpect(status().isCreated());

		assertProblem(api().postJson("/api/v1/users", body), 409, PREFIX + "email-already-registered",
				"/api/v1/users");
	}

	@Test
	void slotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, START, END);

		assertProblem(api().postSlot(user, "2030-01-01T09:30:00Z", "2030-01-01T10:30:00Z"), 409,
				PREFIX + "slot-overlap", "/api/v1/users/" + user + "/slots");
	}

	@Test
	void slotNotFree() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		api().patchStatus(organizer, slot, "BUSY").andExpect(status().isOk());

		assertProblem(api().postMeeting(organizer, slot, "Planning"), 409, PREFIX + "slot-not-free",
				"/api/v1/users/" + organizer + "/meetings");
	}

	@Test
	void slotBooked() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		api().postMeeting(organizer, slot, "Planning").andExpect(status().isCreated());
		String path = "/api/v1/users/" + organizer + "/slots/" + slot;

		assertProblem(mockMvc.perform(delete(path)), 409, PREFIX + "slot-booked", path);
		assertProblem(api().patchStatus(organizer, slot, "FREE"), 409, PREFIX + "slot-booked", path + "/availability");
	}

	@Test
	void errorsArrayIsOnlyPresentForValidationErrors() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, START, END);

		JsonNode notFound = assertProblem(mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())), 404,
				PREFIX + "user-not-found", null);
		JsonNode conflict = assertProblem(api().postSlot(user, START, END), 409, PREFIX + "slot-overlap", null);
		JsonNode malformed = assertProblem(api().postJson("/api/v1/users", "{"), 400, PREFIX + "malformed-request",
				null);

		assertThat(notFound.has("errors")).isFalse();
		assertThat(conflict.has("errors")).isFalse();
		assertThat(malformed.has("errors")).isFalse();
	}

	// ---------------------------------------------------------------- framework errors

	@Test
	void unknownPathIsNotFoundProblemWithAboutBlankType() throws Exception {
		String path = "/api/v1/calendars";

		assertProblem(mockMvc.perform(get(path)), 404, "about:blank", path);
	}

	@Test
	void noCalendarEndpointsExistUnderAnyUserPath() throws Exception {
		UUID user = api().createUser();

		for (String path : new String[] { "/api/v1/users/" + user + "/calendars", "/api/v1/calendars/" + user,
				"/api/v1/users/" + user + "/calendar" }) {
			assertProblem(mockMvc.perform(get(path)), 404, "about:blank", path);
		}
		assertProblem(api().postJson("/api/v1/calendars", "{}"), 404, "about:blank", "/api/v1/calendars");
	}

	@Test
	void deleteOnUserIsMethodNotAllowedProblem() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user;

		ResultActions result = mockMvc.perform(delete(path));

		assertProblem(result, 405, "about:blank", path);
		assertThat(result.andReturn().getResponse().getHeader("Allow")).contains("GET");
	}

	@Test
	void putOnUserCollectionIsMethodNotAllowedProblem() throws Exception {
		String path = "/api/v1/users";

		ResultActions result = mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"));

		assertProblem(result, 405, "about:blank", path);
		assertThat(result.andReturn().getResponse().getHeader("Allow")).contains("POST");
	}

	@ParameterizedTest
	@ValueSource(strings = { "text/plain", "application/xml", "application/x-www-form-urlencoded" })
	void unsupportedMediaTypeIsProblem(String contentType) throws Exception {
		String path = "/api/v1/users";

		assertProblem(mockMvc.perform(post(path).contentType(contentType).content("name=a&email=a@b.com")), 415,
				"about:blank", path);
	}

	@Test
	void missingContentTypeOnBodyEndpointIsUnsupportedMediaType() throws Exception {
		String path = "/api/v1/users";

		assertProblem(mockMvc.perform(post(path).content("{\"name\":\"a\",\"email\":\"a@b.com\"}")), 415,
				"about:blank", path);
	}

	@Test
	void errorStillUsesProblemJsonWhenClientAcceptsOnlyApplicationJson() throws Exception {
		UUID user = api().createUser();
		String missingUser = "/api/v1/users/" + UUID.randomUUID();
		String malformedId = "/api/v1/users/not-a-uuid";
		String unknownPath = "/api/v1/calendars";
		String missingSlot = "/api/v1/users/" + user + "/slots/" + UUID.randomUUID();

		assertProblem(mockMvc.perform(get(missingUser).accept(MediaType.APPLICATION_JSON)), 404,
				PREFIX + "user-not-found", missingUser);
		assertProblem(mockMvc.perform(get(malformedId).accept(MediaType.APPLICATION_JSON)), 400,
				PREFIX + "malformed-request", malformedId);
		assertProblem(mockMvc.perform(get(unknownPath).accept(MediaType.APPLICATION_JSON)), 404, "about:blank",
				unknownPath);
		assertProblem(mockMvc.perform(get(missingSlot).accept(MediaType.APPLICATION_JSON)), 404,
				PREFIX + "slot-not-found", missingSlot);
	}

	@Test
	void acceptXmlOnNormalEndpointIsNotAcceptableProblem() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user;

		assertProblem(mockMvc.perform(get(path).accept(MediaType.APPLICATION_XML)), 406, "about:blank", path);
	}

	@Test
	void acceptXmlOnListEndpointIsNotAcceptableProblem() throws Exception {
		UUID user = api().createUser();
		String path = "/api/v1/users/" + user + "/slots";

		assertProblem(mockMvc.perform(get(path).param("from", START).param("to", END).accept(MediaType.APPLICATION_XML)),
				406, "about:blank", path);
	}

	// ---------------------------------------------------------------- robustness: never 500

	@Test
	void absurdlyLongStringsInUserBodyAreRejectedWith400() throws Exception {
		String huge = "a".repeat(1_000_000);

		assertProblem(api().postJson("/api/v1/users", "{\"name\":\"" + huge + "\",\"email\":\"a@example.com\"}"), 400,
				PREFIX + "validation-error", "/api/v1/users");
		assertProblem(api().postJson("/api/v1/users", "{\"name\":\"Bob\",\"email\":\"" + huge + "@example.com\"}"), 400,
				PREFIX + "validation-error", "/api/v1/users");
	}

	@Test
	void absurdlyLongStringsInMeetingBodyAreRejectedWith400() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		String path = "/api/v1/users/" + organizer + "/meetings";
		String huge = "t".repeat(1_000_000);

		assertProblem(api().postMeeting(organizer, slot, huge), 400, PREFIX + "validation-error", path);
		assertProblem(api().postJson(path, "{\"slotId\":\"" + slot + "\",\"title\":\"ok\",\"description\":\"" + huge
				+ "\"}"), 400, PREFIX + "validation-error", path);
	}

	@Test
	void absurdlyLongPathSegmentAndQueryValueAreRejectedWith400() throws Exception {
		UUID user = api().createUser();
		String hugePath = "/api/v1/users/" + "9".repeat(100_000);
		String availability = "/api/v1/users/" + user + "/availability";

		assertProblem(mockMvc.perform(get(hugePath)), 400, PREFIX + "malformed-request", hugePath);
		assertProblem(mockMvc.perform(get(availability).param("from", "2".repeat(100_000)).param("to", END)), 400,
				PREFIX + "malformed-request", availability);
	}

	@Test
	void nulCharacterInStringIsNeverAServerError() throws Exception {
		// PostgreSQL text columns cannot store U+0000; the API must answer with a 4xx (or accept it), not a 500.
		int userStatus = api().postJson("/api/v1/users",
				"{\"name\":\"a\\u0000b\",\"email\":\"nul-" + UUID.randomUUID() + "@example.com\"}").andReturn()
				.getResponse().getStatus();
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		int meetingStatus = api().postMeeting(organizer, slot, "a\\u0000b").andReturn().getResponse().getStatus();

		SoftAssertions.assertSoftly(softly -> {
			softly.assertThat(userStatus).as("POST user with NUL in name").isLessThan(500);
			softly.assertThat(meetingStatus).as("POST meeting with NUL in title").isLessThan(500);
		});
	}

	@Test
	void slotsInYear0001And9999AreAcceptedAndReadBackUnchanged() throws Exception {
		UUID user = api().createUser();

		JsonNode ancient = api().json(api().postSlot(user, "0001-01-01T00:00:00Z", "0001-01-01T01:00:00Z")
				.andExpect(status().isCreated()));
		JsonNode distant = api().json(api().postSlot(user, "9999-12-31T22:00:00Z", "9999-12-31T23:59:59Z")
				.andExpect(status().isCreated()));

		for (JsonNode slot : new JsonNode[] { ancient, distant }) {
			JsonNode read = api().json(mockMvc.perform(get("/api/v1/users/" + user + "/slots/" + slot.get("id").asText()))
					.andExpect(status().isOk()));
			assertThat(read.get("startTime").asText()).isEqualTo(slot.get("startTime").asText());
			assertThat(read.get("endTime").asText()).isEqualTo(slot.get("endTime").asText());
		}
		assertThat(ancient.get("startTime").asText()).isEqualTo("0001-01-01T00:00:00Z");
		assertThat(distant.get("endTime").asText()).isEqualTo("9999-12-31T23:59:59Z");
	}

	@Test
	void queryAcrossYear0001To9999IsAnswered() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, START, END);

		JsonNode availability = api().json(api().getAvailability(user, "0001-01-01T00:00:00Z", "9999-12-31T23:59:59Z")
				.andExpect(status().isOk()));

		assertThat(availability.get("slots")).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { MIN_INSTANT + "|" + MAX_INSTANT, MIN_INSTANT + "|" + "-1000000000-01-01T00:00:01Z",
			"+1000000000-12-31T23:59:58Z|" + MAX_INSTANT, "+10000-01-01T00:00:00Z|+10000-01-01T01:00:00Z",
			"-0001-01-01T00:00:00Z|-0001-01-01T01:00:00Z" })
	void slotWithExtremeInstantsIsNeverAServerError(String range) throws Exception {
		UUID user = api().createUser();
		String[] bounds = range.split("\\|");

		ResultActions result = api().postSlot(user, bounds[0], bounds[1]);

		int status = result.andReturn().getResponse().getStatus();
		assertThat(status).as("status for slot %s", range).isBetween(200, 499);
		if (status >= 400) {
			assertProblem(result, status, null, "/api/v1/users/" + user + "/slots");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "slots", "meetings", "availability" })
	void queryWithExtremeInstantsIsNeverAServerError(String resource) throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, START, END);
		String path = "/api/v1/users/" + user + "/" + resource;

		SoftAssertions softly = new SoftAssertions();
		for (String[] range : new String[][] { { MIN_INSTANT, MAX_INSTANT }, { MIN_INSTANT, START },
				{ START, MAX_INSTANT }, { MAX_INSTANT, MAX_INSTANT }, { MAX_INSTANT, MIN_INSTANT } }) {
			int status = mockMvc.perform(get(path).param("from", range[0]).param("to", range[1])).andReturn()
					.getResponse().getStatus();
			softly.assertThat(status).as("GET %s from=%s to=%s", path, range[0], range[1]).isIn(200, 400);
		}
		softly.assertAll();
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"name\":123,\"email\":\"a@example.com\"}", "{\"name\":[\"x\"],\"email\":\"a@example.com\"}",
			"{\"name\":{\"first\":\"x\"},\"email\":\"a@example.com\"}", "{\"name\":true,\"email\":\"a@example.com\"}",
			"{\"name\":\"Bob\",\"email\":12345}", "[]", "\"just a string\"", "null", "42" })
	void userBodyWithWrongJsonTypesIsMalformedRequest(String body) throws Exception {
		// unique email so that a (wrongly) accepted body can never collide with another test's user
		String unique = body.replace("a@example.com", "types-" + UUID.randomUUID() + "@example.com");

		assertProblem(api().postJson("/api/v1/users", unique), 400, PREFIX + "malformed-request", "/api/v1/users");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"startTime\":1700000000,\"endTime\":\"2030-01-01T10:00:00Z\"}",
			"{\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":{\"x\":1}}",
			"{\"startTime\":[\"2030-01-01T09:00:00Z\"],\"endTime\":\"2030-01-01T10:00:00Z\"}",
			"{\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\",\"status\":7}",
			"{\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\",\"status\":{}}" })
	void slotBodyWithWrongJsonTypesIsMalformedRequest(String body) throws Exception {
		UUID user = api().createUser();

		assertProblem(api().postJson("/api/v1/users/" + user + "/slots", body), 400, PREFIX + "malformed-request",
				"/api/v1/users/" + user + "/slots");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"slotId\":{\"id\":1},\"title\":\"t\"}", "{\"slotId\":[],\"title\":\"t\"}",
			"{\"slotId\":12,\"title\":\"t\"}", "{\"slotId\":\"%s\",\"title\":\"t\",\"participantIds\":\"abc\"}",
			"{\"slotId\":\"%s\",\"title\":\"t\",\"participantIds\":{\"a\":1}}",
			"{\"slotId\":\"%s\",\"title\":\"t\",\"participantIds\":[1,2]}",
			"{\"slotId\":\"%s\",\"title\":\"t\",\"participantIds\":[{}]}",
			"{\"slotId\":\"%s\",\"title\":{\"a\":1}}", "{\"slotId\":\"%s\",\"title\":\"t\",\"description\":[1]}" })
	void meetingBodyWithWrongJsonTypesIsMalformedRequest(String bodyTemplate) throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		String path = "/api/v1/users/" + organizer + "/meetings";

		assertProblem(api().postJson(path, bodyTemplate.replace("%s", slot.toString())), 400,
				PREFIX + "malformed-request", path);
	}

	@Test
	void moreThanHundredParticipantsIsValidationError() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, START, END);
		String path = "/api/v1/users/" + organizer + "/meetings";
		String participants = IntStream.range(0, 101).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
				.collect(Collectors.joining(","));

		JsonNode problem = assertProblem(api().postJson(path,
				"{\"slotId\":\"" + slot + "\",\"title\":\"Big\",\"participantIds\":[" + participants + "]}"), 400,
				PREFIX + "validation-error", path);

		assertThat(fieldsOf(problem)).containsExactly("participantIds");
	}

	@Test
	void hugeParticipantListForUnknownOrganizerIsStillValidationError() throws Exception {
		String path = "/api/v1/users/" + UUID.randomUUID() + "/meetings";
		String participants = IntStream.range(0, 5_000).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
				.collect(Collectors.joining(","));

		assertProblem(api().postJson(path,
				"{\"slotId\":\"" + UUID.randomUUID() + "\",\"title\":\"Big\",\"participantIds\":[" + participants
						+ "]}"),
				400, PREFIX + "validation-error", path);
	}

	@Test
	void deeplyNestedJsonIsRejectedWith400() throws Exception {
		String nested = "[".repeat(5_000) + "]".repeat(5_000);

		assertProblem(api().postJson("/api/v1/users", nested), 400, PREFIX + "malformed-request", "/api/v1/users");
		assertProblem(api().postJson("/api/v1/users", "{\"name\":" + nested + ",\"email\":\"a@example.com\"}"), 400,
				PREFIX + "malformed-request", "/api/v1/users");
	}

	@Test
	void emptyBodyOnBodyEndpointIsMalformedRequest() throws Exception {
		assertProblem(api().postJson("/api/v1/users", ""), 400, PREFIX + "malformed-request", "/api/v1/users");
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Asserts the RFC 9457 contract of one error response and returns the parsed body.
	 *
	 * @param type expected {@code type}, or {@code null} to only require a non-empty one
	 * @param path expected {@code instance} (the request path), or {@code null} to only require a non-empty one
	 */
	private JsonNode assertProblem(ResultActions result, int status, String type, String path) throws Exception {
		MvcResult mvcResult = result.andExpect(status().is(status)).andReturn();
		MockHttpServletResponse response = mvcResult.getResponse();

		assertThat(response.getContentType()).as("Content-Type").isNotNull();
		MediaType contentType = MediaType.parseMediaType(response.getContentType());
		assertThat(contentType.getType()).isEqualTo("application");
		assertThat(contentType.getSubtype()).as("Content-Type %s", contentType).isEqualTo("problem+json");

		JsonNode problem = objectMapper.readTree(response.getContentAsString());
		assertThat(problem.get("status").isInt()).isTrue();
		assertThat(problem.get("status").asInt()).isEqualTo(status);
		assertThat(problem.get("type").asText()).isNotBlank();
		if (type != null) {
			assertThat(problem.get("type").asText()).isEqualTo(type);
		}
		assertThat(problem.path("title").asText()).as("title").isNotBlank();
		assertThat(problem.path("detail").asText()).as("detail").isNotBlank();
		if (path != null) {
			assertThat(problem.path("instance").asText()).as("instance").isEqualTo(path);
		}
		else {
			assertThat(problem.path("instance").asText()).as("instance").isNotBlank();
		}

		boolean validation = (PREFIX + "validation-error").equals(problem.get("type").asText());
		if (validation) {
			assertThat(problem.get("errors")).as("errors of a validation-error").isNotNull();
			assertThat(problem.get("errors").isArray()).isTrue();
			assertThat(problem.get("errors")).isNotEmpty();
			for (JsonNode error : problem.get("errors")) {
				assertThat(error.get("field").isTextual()).isTrue();
				assertThat(error.get("field").asText()).isNotBlank();
				assertThat(error.get("message").isTextual()).isTrue();
				assertThat(error.get("message").asText()).isNotBlank();
			}
		}
		else {
			assertThat(problem.has("errors")).as("errors only exist for validation-error").isFalse();
		}
		return problem;
	}

	private static List<String> fieldsOf(JsonNode problem) {
		return problem.get("errors").findValuesAsText("field");
	}

}
