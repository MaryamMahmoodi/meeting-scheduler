package com.demo.meetingscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Guards against drift between the canonical contract (docs/openapi.yaml) and the implementation:
 * the set of routes and parameters is compared with what springdoc derives from the controllers,
 * the served file is compared with the file on disk, and real responses of a realistic scenario are
 * validated against the response schemas of the contract.
 */
class OpenApiContractTest extends IntegrationTest {

	private static final Contract CONTRACT = Contract.load(Path.of("docs/openapi.yaml"));
	private static final SchemaValidator SCHEMAS = new SchemaValidator(CONTRACT);
	private static final String API_PREFIX = "/api/v1";

	// ---------------------------------------------------------------- routes and parameters

	@Test
	void contractAndGeneratedApiExposeTheSameMethodPathPairs() throws Exception {
		Set<String> contractRoutes = new TreeSet<>();
		CONTRACT.operations().forEach(op -> contractRoutes.add(op.route()));

		Set<String> generatedRoutes = new TreeSet<>();
		generatedApiDocs().path("paths").fields().forEachRemaining(path -> {
			if (path.getKey().startsWith(API_PREFIX)) {
				path.getValue().fieldNames().forEachRemaining(
						method -> generatedRoutes.add(method.toUpperCase() + " " + path.getKey()));
			}
		});

		assertThat(contractRoutes).isNotEmpty();
		assertThat(generatedRoutes).isEqualTo(contractRoutes);
	}

	@Test
	void operationIdsEqualTheControllerMethodNamesGeneratedBySpringdoc() throws Exception {
		JsonNode paths = generatedApiDocs().path("paths");

		SoftAssertions softly = new SoftAssertions();
		for (Operation op : CONTRACT.operations()) {
			JsonNode generated = paths.path(op.path()).path(op.method());
			softly.assertThat(generated.path("operationId").asText("<missing>")).as("operationId of %s", op.route())
					.isEqualTo(op.id());
		}
		softly.assertAll();
	}

	@Test
	void everyContractParameterExistsInTheGeneratedApiWithSameLocationRequirednessAndType() throws Exception {
		JsonNode paths = generatedApiDocs().path("paths");

		SoftAssertions softly = new SoftAssertions();
		for (Operation op : CONTRACT.operations()) {
			JsonNode generatedParameters = paths.path(op.path()).path(op.method()).path("parameters");
			Set<String> contractNames = new TreeSet<>();
			for (Map<String, Object> declared : CONTRACT.parameters(op)) {
				String name = (String) declared.get("name");
				contractNames.add(name);
				JsonNode generated = find(generatedParameters, name);
				softly.assertThat(generated).as("parameter '%s' of %s is generated", name, op.route()).isNotNull();
				if (generated == null) {
					continue;
				}
				softly.assertThat(generated.path("in").asText()).as("'in' of '%s' in %s", name, op.route())
						.isEqualTo(declared.get("in"));
				boolean declaredRequired = Boolean.TRUE.equals(declared.get("required"));
				softly.assertThat(generated.path("required").asBoolean(false))
						.as("'required' of '%s' in %s", name, op.route()).isEqualTo(declaredRequired);
				Map<String, Object> schema = CONTRACT.resolve(declared.get("schema"));
				softly.assertThat(generated.path("schema").path("type").asText())
						.as("type of '%s' in %s", name, op.route()).isEqualTo(schema.get("type"));
				softly.assertThat(generated.path("schema").path("format").asText())
						.as("format of '%s' in %s", name, op.route()).isEqualTo(schema.get("format"));
			}
			Set<String> generatedNames = new TreeSet<>();
			generatedParameters.forEach(parameter -> generatedNames.add(parameter.path("name").asText()));
			softly.assertThat(generatedNames).as("parameters generated for %s", op.route())
					.isEqualTo(contractNames);
		}
		softly.assertAll();
	}

	@Test
	void contractHasNoCalendarPathsAndEveryPathStartsWithApiV1() throws Exception {
		Set<String> paths = new TreeSet<>();
		CONTRACT.operations().forEach(op -> paths.add(op.path()));

		assertThat(paths).isNotEmpty().allSatisfy(path -> assertThat(path).startsWith(API_PREFIX + "/"));
		assertThat(paths).noneSatisfy(path -> assertThat(path.toLowerCase()).contains("calendar"));
		generatedApiDocs().path("paths").fieldNames().forEachRemaining(path -> {
			if (path.startsWith(API_PREFIX)) {
				assertThat(path.toLowerCase()).doesNotContain("calendar");
			}
		});
	}

	@Test
	void servedOpenApiYamlIsByteIdenticalToTheContractFile() throws Exception {
		byte[] served = mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsByteArray();

		assertThat(served).isEqualTo(Files.readAllBytes(Path.of("docs/openapi.yaml")));
	}

	// ---------------------------------------------------------------- response shapes

	@Test
	void realSuccessResponsesConformToTheContractSchemasStatusCodesAndLocations() throws Exception {
		Set<String> covered = new HashSet<>();
		UUID alice = id(call(covered, "createUser", jsonPost("/api/v1/users", userBody("Alice"))));
		UUID bob = id(call(covered, "createUser", jsonPost("/api/v1/users", userBody("Bob"))));
		UUID carol = id(call(covered, "createUser", jsonPost("/api/v1/users", userBody("Carol"))));
		call(covered, "getUser", get("/api/v1/users/" + alice));

		String slots = "/api/v1/users/" + alice + "/slots";
		JsonNode planningSlot = call(covered, "createSlot", jsonPost(slots, slotBody("09:00", "10:00")));
		JsonNode quickSlot = call(covered, "createSlot", jsonPost(slots, slotBody("11:00", "12:00")));
		JsonNode movableSlot = call(covered, "createSlot", jsonPost(slots, slotBody("13:00", "14:00")));
		JsonNode doomedSlot = call(covered, "createSlot", jsonPost(slots, slotBody("15:00", "16:00")));
		call(covered, "getSlot", get(slots + "/" + id(planningSlot)));

		String meetings = "/api/v1/users/" + alice + "/meetings";
		JsonNode planning = call(covered, "createMeeting", jsonPost(meetings, "{\"slotId\":\"" + id(planningSlot)
				+ "\",\"title\":\"Sprint planning\",\"description\":\"Plan the sprint.\",\"participantIds\":[\""
				+ carol + "\",\"" + bob + "\"]}"));
		JsonNode quick = call(covered, "createMeeting",
				jsonPost(meetings, "{\"slotId\":\"" + id(quickSlot) + "\",\"title\":\"Quick sync\"}"));
		call(covered, "getMeeting", get(meetings + "/" + id(planning)));
		call(covered, "getMeeting", get("/api/v1/users/" + bob + "/meetings/" + id(planning)));

		String range = "?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z";
		JsonNode listedSlots = call(covered, "listSlots", get(slots + range));
		JsonNode listedMeetings = call(covered, "listMeetings", get(meetings + range));
		JsonNode bobsMeetings = call(covered, "listMeetings",
				get("/api/v1/users/" + bob + "/meetings" + range));
		JsonNode availability = call(covered, "getAvailability",
				get("/api/v1/users/" + alice + "/availability" + range));

		call(covered, "updateSlotAvailability", patch(slots + "/" + id(movableSlot) + "/availability")
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BUSY\"}"));
		JsonNode moved = call(covered, "updateSlot", put(slots + "/" + id(movableSlot))
				.contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"2030-01-01T13:30:00Z\","
						+ "\"endTime\":\"2030-01-01T14:30:00Z\",\"status\":\"FREE\"}"));
		call(covered, "deleteSlot", delete(slots + "/" + id(doomedSlot)));

		// the scenario must exercise every operation of the contract, otherwise a new operation escapes the check
		Set<String> contractOperations = new TreeSet<>();
		CONTRACT.operations().forEach(op -> contractOperations.add(op.id()));
		assertThat(covered).isEqualTo(contractOperations);

		// a few semantic spot checks on top of the schema conformance
		assertThat(planning.get("description").asText()).isEqualTo("Plan the sprint.");
		assertThat(quick.get("description").isNull()).as("absent description is returned as null").isTrue();
		assertThat(quick.get("participantIds")).isEmpty();
		assertThat(texts(planning.get("participantIds"))).isSorted().containsExactlyInAnyOrder(bob.toString(),
				carol.toString());
		assertThat(planning.get("startTime").asText()).isEqualTo(planningSlot.get("startTime").asText());
		assertThat(planning.get("endTime").asText()).isEqualTo(planningSlot.get("endTime").asText());
		assertThat(texts(listedSlots, "startTime")).isSorted().hasSize(4);
		assertThat(texts(listedMeetings, "id")).containsExactly(id(planning).toString(), id(quick).toString());
		assertThat(texts(bobsMeetings, "id")).containsExactly(id(planning).toString());
		assertThat(texts(availability.get("slots"), "status")).containsExactly("BUSY", "BUSY", "FREE", "FREE");
		assertThat(moved.get("status").asText()).isEqualTo("FREE");
		assertThat(moved.get("startTime").asText()).isEqualTo("2030-01-01T13:30:00Z");
	}

	@Test
	void realErrorResponsesUseDocumentedStatusCodesAndConformToProblemDetail() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID bookedSlot = api().createSlot(organizer, "2030-02-01T09:00:00Z", "2030-02-01T10:00:00Z");
		UUID freeSlot = api().createSlot(organizer, "2030-02-01T11:00:00Z", "2030-02-01T12:00:00Z");
		UUID meeting = api().id(api().postMeeting(organizer, bookedSlot, "Booked", participant)
				.andExpect(status().isCreated()));
		UUID unknown = UUID.randomUUID();
		String user = "/api/v1/users/" + organizer;
		String slot = user + "/slots/" + freeSlot;
		String booked = user + "/slots/" + bookedSlot;
		String range = "?from=2030-02-01T00:00:00Z&to=2030-02-02T00:00:00Z";
		String badRange = "?from=2030-02-02T00:00:00Z&to=2030-02-01T00:00:00Z";
		String validSlot = slotBody("09:30", "10:30");
		String overlapping = "{\"startTime\":\"2030-02-01T09:30:00Z\",\"endTime\":\"2030-02-01T10:30:00Z\"}";
		String nameless = "{\"name\":\"\",\"email\":\"x\"}";
		String takenEmail = "{\"name\":\"Dup\",\"email\":\"" + dupEmail() + "\"}";
		String badStatus = "{\"status\":\"MAYBE\"}";
		String meetingForBooked = "{\"slotId\":\"" + bookedSlot + "\",\"title\":\"Again\"}";

		List<ErrorCase> cases = List.of(
				new ErrorCase("createUser", 400, jsonPost("/api/v1/users", nameless)),
				new ErrorCase("createUser", 409, jsonPost("/api/v1/users", takenEmail)),
				new ErrorCase("getUser", 400, get("/api/v1/users/not-a-uuid")),
				new ErrorCase("getUser", 404, get("/api/v1/users/" + unknown)),
				new ErrorCase("createSlot", 400, jsonPost(user + "/slots", slotBody("10:00", "09:00"))),
				new ErrorCase("createSlot", 404, jsonPost("/api/v1/users/" + unknown + "/slots", validSlot)),
				new ErrorCase("createSlot", 409, jsonPost(user + "/slots", overlapping)),
				new ErrorCase("listSlots", 400, get(user + "/slots" + badRange)),
				new ErrorCase("listSlots", 404, get("/api/v1/users/" + unknown + "/slots" + range)),
				new ErrorCase("getSlot", 400, get(user + "/slots/not-a-uuid")),
				new ErrorCase("getSlot", 404, get(user + "/slots/" + unknown)),
				new ErrorCase("updateSlot", 400, put(slot).contentType(MediaType.APPLICATION_JSON).content("{}")),
				new ErrorCase("updateSlot", 404, put(user + "/slots/" + unknown)
						.contentType(MediaType.APPLICATION_JSON).content(validSlot)),
				new ErrorCase("updateSlot", 409, put(booked).contentType(MediaType.APPLICATION_JSON)
						.content(validSlot)),
				new ErrorCase("deleteSlot", 400, delete(user + "/slots/not-a-uuid")),
				new ErrorCase("deleteSlot", 404, delete(user + "/slots/" + unknown)),
				new ErrorCase("deleteSlot", 409, delete(booked)),
				new ErrorCase("updateSlotAvailability", 400, patch(slot + "/availability")
						.contentType(MediaType.APPLICATION_JSON).content(badStatus)),
				new ErrorCase("updateSlotAvailability", 404, patch(user + "/slots/" + unknown + "/availability")
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BUSY\"}")),
				new ErrorCase("updateSlotAvailability", 409, patch(booked + "/availability")
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BUSY\"}")),
				new ErrorCase("createMeeting", 400, jsonPost(user + "/meetings", "{\"title\":\"no slot\"}")),
				new ErrorCase("createMeeting", 404, jsonPost(user + "/meetings",
						"{\"slotId\":\"" + unknown + "\",\"title\":\"ghost\"}")),
				new ErrorCase("createMeeting", 409, jsonPost(user + "/meetings", meetingForBooked)),
				new ErrorCase("listMeetings", 400, get(user + "/meetings" + badRange)),
				new ErrorCase("listMeetings", 404, get("/api/v1/users/" + unknown + "/meetings" + range)),
				new ErrorCase("getMeeting", 400, get(user + "/meetings/not-a-uuid")),
				new ErrorCase("getMeeting", 404, get(user + "/meetings/" + unknown)),
				new ErrorCase("getAvailability", 400, get(user + "/availability" + badRange)),
				new ErrorCase("getAvailability", 404, get("/api/v1/users/" + unknown + "/availability" + range)));

		assertThat(meeting).isNotNull();
		SoftAssertions softly = new SoftAssertions();
		for (ErrorCase errorCase : cases) {
			verifyDocumentedError(softly, errorCase);
		}
		softly.assertAll();
	}

	@Test
	void everyOperationDocumentsTheErrorStatusesItCanReturnAsProblemJson() {
		SoftAssertions softly = new SoftAssertions();
		for (Operation op : CONTRACT.operations()) {
			for (String code : CONTRACT.responseCodes(op)) {
				if (code.startsWith("2")) {
					continue;
				}
				Map<String, Object> schema = CONTRACT.responseSchema(op, code, "application/problem+json");
				softly.assertThat(schema).as("problem+json schema of %s %s", op.id(), code).isNotNull();
				if (schema != null) {
					softly.assertThat(schema.get("$ref")).as("schema of %s %s", op.id(), code)
							.isEqualTo("#/components/schemas/ProblemDetail");
				}
			}
		}
		softly.assertAll();
	}

	@Test
	void theSchemaWalkerReportsViolations() {
		Map<String, Object> slot = CONTRACT.schema("TimeSlot");
		String valid = "{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
				+ "\",\"startTime\":\"2030-01-01T09:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\","
				+ "\"status\":\"FREE\",\"createdAt\":\"2030-01-01T08:00:00.123456Z\"}";

		assertThat(SCHEMAS.validate(slot, read(valid))).isEmpty();
		assertThat(SCHEMAS.validate(slot, read(valid.replace("\"status\":\"FREE\",", "")))).anyMatch(
				violation -> violation.contains("status") && violation.contains("missing"));
		assertThat(SCHEMAS.validate(slot, read(valid.replace("\"status\":\"FREE\"", "\"status\":\"MAYBE\""))))
				.anyMatch(violation -> violation.contains("enum"));
		assertThat(SCHEMAS.validate(slot, read(valid.replace("\"status\":\"FREE\"", "\"status\":\"FREE\",\"extra\":1"))))
				.anyMatch(violation -> violation.contains("extra") && violation.contains("not declared"));
		assertThat(SCHEMAS.validate(slot, read(valid.replace("2030-01-01T09:00:00Z", "tomorrow")))).anyMatch(
				violation -> violation.contains("date-time"));
		assertThat(SCHEMAS.validate(slot, read(valid.replace("2030-01-01T09:00:00Z", "2030-01-01T10:00:00+01:00"))))
				.anyMatch(violation -> violation.contains("UTC"));
		assertThat(SCHEMAS.validate(slot, read(valid.replaceFirst("\"id\":\"[^\"]*\"", "\"id\":\"nope\""))))
				.anyMatch(violation -> violation.contains("uuid"));
		assertThat(SCHEMAS.validate(slot, read(valid.replace("\"status\":\"FREE\"", "\"status\":null"))))
				.anyMatch(violation -> violation.contains("null"));
		assertThat(SCHEMAS.validate(CONTRACT.schema("Meeting"), read("{}"))).isNotEmpty();
		assertThat(SCHEMAS.validate(CONTRACT.schema("Meeting"), read("[]"))).anyMatch(
				violation -> violation.contains("expected"));
	}

	// ---------------------------------------------------------------- helpers: HTTP

	private JsonNode generatedApiDocs() throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString());
	}

	private static JsonNode find(JsonNode parameters, String name) {
		for (JsonNode parameter : parameters) {
			if (name.equals(parameter.path("name").asText())) {
				return parameter;
			}
		}
		return null;
	}

	/**
	 * Performs a request of the named operation and checks it against the contract: documented success
	 * status, media type, response schema and, for 201, a Location header that resolves to the resource.
	 * Returns the parsed body, or {@code null} for responses without content.
	 */
	private JsonNode call(Set<String> covered, String operationId, MockHttpServletRequestBuilder request)
			throws Exception {
		Operation op = CONTRACT.operation(operationId);
		MvcResult result = mockMvc.perform(request).andReturn();
		MockHttpServletResponse response = result.getResponse();
		String documentedStatus = CONTRACT.successStatus(op);
		assertThat(String.valueOf(response.getStatus())).as("status of %s", op.route()).isEqualTo(documentedStatus);
		covered.add(operationId);

		Map<String, Object> schema = CONTRACT.responseSchema(op, documentedStatus, "application/json");
		if (schema == null) {
			assertThat(response.getContentAsByteArray()).as("%s documents no body", op.route()).isEmpty();
			return null;
		}
		assertThat(response.getContentType()).as("Content-Type of %s", op.route()).isNotNull();
		assertThat(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())))
				.as("Content-Type %s of %s", response.getContentType(), op.route()).isTrue();
		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(SCHEMAS.validate(schema, body)).as("body of %s", op.route()).isEmpty();

		if ("201".equals(documentedStatus)) {
			assertThat(CONTRACT.response(op, "201")).as("201 of %s declares a Location header", op.route())
					.containsKey("headers");
			String location = response.getHeader("Location");
			assertThat(location).as("Location header of %s", op.route()).isNotBlank();
			assertThat(URI.create(location).getPath()).startsWith(API_PREFIX + "/");
			JsonNode followed = objectMapper.readTree(mockMvc.perform(get(location)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString());
			assertThat(SCHEMAS.validate(schema, followed)).as("body behind Location of %s", op.route()).isEmpty();
			assertThat(followed).as("resource behind Location of %s", op.route()).isEqualTo(body);
		}
		return body;
	}

	private void verifyDocumentedError(SoftAssertions softly, ErrorCase errorCase) throws Exception {
		Operation op = CONTRACT.operation(errorCase.operationId());
		String code = String.valueOf(errorCase.status());
		String label = op.id() + " -> " + code;
		MockHttpServletResponse response = mockMvc.perform(errorCase.request()).andReturn().getResponse();

		softly.assertThat(response.getStatus()).as("status of %s", label).isEqualTo(errorCase.status());
		softly.assertThat(CONTRACT.responseCodes(op)).as("%s is documented for %s", code, op.route()).contains(code);
		Map<String, Object> schema = CONTRACT.responseSchema(op, code, "application/problem+json");
		softly.assertThat(schema).as("problem+json schema documented for %s", label).isNotNull();
		softly.assertThat(String.valueOf(response.getContentType())).as("Content-Type of %s", label)
				.startsWith("application/problem+json");
		if (schema != null && response.getContentAsByteArray().length > 0) {
			JsonNode body = read(response.getContentAsString());
			softly.assertThat(SCHEMAS.validate(schema, body)).as("body of %s", label).isEmpty();
			softly.assertThat(body.path("status").asInt()).as("problem status of %s", label)
					.isEqualTo(errorCase.status());
		}
	}

	private record ErrorCase(String operationId, int status, MockHttpServletRequestBuilder request) {
	}

	private static MockHttpServletRequestBuilder jsonPost(String path, String body) {
		return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String userBody(String name) {
		return "{\"name\":\"" + name + "\",\"email\":\"" + name.toLowerCase() + "-" + UUID.randomUUID()
				+ "@example.com\"}";
	}

	/** Registers a user and returns an email body fragment that is already taken. */
	private String dupEmail() throws Exception {
		String email = "taken-" + UUID.randomUUID() + "@example.com";
		api().postJson("/api/v1/users", "{\"name\":\"Taken\",\"email\":\"" + email + "\"}")
				.andExpect(status().isCreated());
		return email;
	}

	private static String slotBody(String from, String to) {
		return "{\"startTime\":\"2030-01-01T" + from + ":00Z\",\"endTime\":\"2030-01-01T" + to + ":00Z\"}";
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.get("id").asText());
	}

	private JsonNode read(String json) {
		try {
			return objectMapper.readTree(json);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(item -> values.add(item.asText()));
		return values;
	}

	private static List<String> texts(JsonNode array, String field) {
		List<String> values = new ArrayList<>();
		array.forEach(item -> values.add(item.get(field).asText()));
		return values;
	}

	// ---------------------------------------------------------------- helpers: contract and schema walker

	/** One (method, path) pair of the contract. */
	private record Operation(String id, String method, String path, Map<String, Object> spec,
			Map<String, Object> pathItem) {

		String route() {
			return method.toUpperCase() + " " + path;
		}

	}

	/** The parsed docs/openapi.yaml with just enough helpers to navigate it. */
	private static final class Contract {

		private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head",
				"options", "trace");

		private final Map<String, Object> root;
		private final Map<String, Operation> operations = new LinkedHashMap<>();

		private Contract(Map<String, Object> root) {
			this.root = root;
			map(root.get("paths")).forEach((path, item) -> {
				Map<String, Object> pathItem = map(item);
				pathItem.forEach((method, spec) -> {
					if (HTTP_METHODS.contains(method)) {
						Map<String, Object> operation = map(spec);
						String id = (String) operation.get("operationId");
						operations.put(id, new Operation(id, method, path, operation, pathItem));
					}
				});
			});
		}

		static Contract load(Path file) {
			try (var reader = Files.newBufferedReader(file)) {
				return new Contract(map(new Yaml(new SafeConstructor(new LoaderOptions())).load(reader)));
			}
			catch (IOException ex) {
				throw new IllegalStateException("Cannot read " + file.toAbsolutePath(), ex);
			}
		}

		@SuppressWarnings("unchecked")
		static Map<String, Object> map(Object node) {
			return (Map<String, Object>) node;
		}

		@SuppressWarnings("unchecked")
		static List<Object> list(Object node) {
			return (List<Object>) node;
		}

		java.util.Collection<Operation> operations() {
			return operations.values();
		}

		Operation operation(String id) {
			assertThat(operations).as("contract operation '%s'", id).containsKey(id);
			return operations.get(id);
		}

		/** Follows a {@code $ref} such as {@code #/components/schemas/User}; other nodes are returned as they are. */
		Map<String, Object> resolve(Object node) {
			Map<String, Object> current = map(node);
			while (current.containsKey("$ref")) {
				String ref = (String) current.get("$ref");
				Object target = root;
				for (String segment : ref.substring(2).split("/")) {
					target = map(target).get(segment);
				}
				current = map(target);
			}
			return current;
		}

		Map<String, Object> schema(String name) {
			return map(map(map(root.get("components")).get("schemas")).get(name));
		}

		/** Path-level and operation-level parameters, with references resolved. */
		List<Map<String, Object>> parameters(Operation op) {
			List<Map<String, Object>> parameters = new ArrayList<>();
			for (Map<String, Object> source : List.of(op.pathItem(), op.spec())) {
				if (source.get("parameters") != null) {
					list(source.get("parameters")).forEach(parameter -> parameters.add(resolve(parameter)));
				}
			}
			return parameters;
		}

		Set<String> responseCodes(Operation op) {
			return map(op.spec().get("responses")).keySet();
		}

		Map<String, Object> response(Operation op, String code) {
			return resolve(map(op.spec().get("responses")).get(code));
		}

		/** The single documented 2xx status code of an operation. */
		String successStatus(Operation op) {
			List<String> success = responseCodes(op).stream().filter(code -> code.startsWith("2")).toList();
			assertThat(success).as("2xx responses of %s", op.route()).hasSize(1);
			return success.get(0);
		}

		/** The schema of a response for a media type, or {@code null} when the response has no such content. */
		Map<String, Object> responseSchema(Operation op, String code, String mediaType) {
			Object content = response(op, code).get("content");
			if (content == null || map(content).get(mediaType) == null) {
				return null;
			}
			return map(map(map(content).get(mediaType)).get("schema"));
		}

	}

	/** A small JSON schema walker for the subset of OpenAPI 3.1 that the contract uses in its responses. */
	private static final class SchemaValidator {

		private static final Pattern UUID_PATTERN = Pattern
				.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

		private final Contract contract;

		SchemaValidator(Contract contract) {
			this.contract = contract;
		}

		List<String> validate(Map<String, Object> schema, JsonNode node) {
			List<String> violations = new ArrayList<>();
			check(schema, node, "$", violations);
			return violations;
		}

		private void check(Object schemaNode, JsonNode node, String at, List<String> violations) {
			Map<String, Object> schema = contract.resolve(schemaNode);
			List<String> types = types(schema);
			if (node.isNull()) {
				if (!types.contains("null")) {
					violations.add(at + ": null is not allowed, expected " + types);
				}
				return;
			}
			if (!types.isEmpty() && types.stream().noneMatch(type -> hasType(node, type))) {
				violations.add(at + ": expected " + types + " but was " + node.getNodeType());
				return;
			}
			if (schema.containsKey("enum") && !Contract.list(schema.get("enum")).contains(node.asText())) {
				violations.add(at + ": '" + node.asText() + "' is not one of the enum values " + schema.get("enum"));
			}
			if (node.isTextual()) {
				checkFormat(schema, node.asText(), at, violations);
			}
			if (node.isIntegralNumber()) {
				checkRange(schema, node.asLong(), at, violations);
			}
			if (node.isObject()) {
				checkObject(schema, node, at, violations);
			}
			if (node.isArray()) {
				checkArray(schema, node, at, violations);
			}
		}

		private void checkObject(Map<String, Object> schema, JsonNode node, String at, List<String> violations) {
			Map<String, Object> properties = schema.get("properties") == null ? Map.of()
					: Contract.map(schema.get("properties"));
			if (schema.get("required") != null) {
				for (Object required : Contract.list(schema.get("required"))) {
					if (!node.has((String) required)) {
						violations.add(at + ": required property '" + required + "' is missing");
					}
				}
			}
			node.fields().forEachRemaining(field -> {
				if (properties.containsKey(field.getKey())) {
					check(properties.get(field.getKey()), field.getValue(), at + "." + field.getKey(), violations);
				}
				else if (!Boolean.TRUE.equals(schema.get("additionalProperties"))) {
					violations.add(at + ": property '" + field.getKey() + "' is not declared in the schema");
				}
			});
		}

		private void checkArray(Map<String, Object> schema, JsonNode node, String at, List<String> violations) {
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < node.size(); i++) {
				if (schema.get("items") != null) {
					check(schema.get("items"), node.get(i), at + "[" + i + "]", violations);
				}
				if (Boolean.TRUE.equals(schema.get("uniqueItems")) && !seen.add(node.get(i).toString())) {
					violations.add(at + "[" + i + "]: duplicate item in a uniqueItems array");
				}
			}
		}

		private static void checkFormat(Map<String, Object> schema, String text, String at, List<String> violations) {
			Object format = schema.get("format");
			if ("uuid".equals(format) && !UUID_PATTERN.matcher(text).matches()) {
				violations.add(at + ": '" + text + "' is not a lower-case uuid");
			}
			if ("date-time".equals(format)) {
				try {
					OffsetDateTime.parse(text);
					if (!text.endsWith("Z")) {
						violations.add(at + ": '" + text + "' is not in UTC (responses use Z)");
					}
				}
				catch (RuntimeException ex) {
					violations.add(at + ": '" + text + "' is not a date-time");
				}
			}
			if ("uri".equals(format)) {
				try {
					URI.create(text);
				}
				catch (RuntimeException ex) {
					violations.add(at + ": '" + text + "' is not a uri");
				}
			}
			if ("email".equals(format) && !text.contains("@")) {
				violations.add(at + ": '" + text + "' is not an email address");
			}
		}

		private static void checkRange(Map<String, Object> schema, long value, String at, List<String> violations) {
			if (schema.get("minimum") instanceof Number minimum && value < minimum.longValue()) {
				violations.add(at + ": " + value + " is below the minimum " + minimum);
			}
			if (schema.get("maximum") instanceof Number maximum && value > maximum.longValue()) {
				violations.add(at + ": " + value + " is above the maximum " + maximum);
			}
		}

		private static List<String> types(Map<String, Object> schema) {
			Object type = schema.get("type");
			if (type == null) {
				return List.of();
			}
			List<String> types = new ArrayList<>();
			(type instanceof List<?> list ? list : List.of(type)).forEach(item -> types.add((String) item));
			return types;
		}

		private static boolean hasType(JsonNode node, String type) {
			return switch (type) {
				case "string" -> node.isTextual();
				case "integer" -> node.isIntegralNumber();
				case "number" -> node.isNumber();
				case "boolean" -> node.isBoolean();
				case "array" -> node.isArray();
				case "object" -> node.isObject();
				default -> false;
			};
		}

	}

}
