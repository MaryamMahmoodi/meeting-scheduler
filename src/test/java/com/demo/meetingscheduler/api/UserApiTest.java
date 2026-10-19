package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Contract tests for {@code POST /api/v1/users} and {@code GET /api/v1/users/{userId}}. */
class UserApiTest extends IntegrationTest {

	private static final String USERS = "/api/v1/users";
	private static final String PROBLEM_PREFIX = "urn:mini-dood:problem:";

	// ---------------------------------------------------------------- create + retrieve

	@Test
	void createUserReturns201WithLocationHeaderAndBody() throws Exception {
		String email = uniqueEmail();

		ResultActions result = api().postJson(USERS, userBody("Alice Example", email))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Alice Example"))
				.andExpect(jsonPath("$.email").value(email));

		JsonNode body = api().json(result);
		UUID id = UUID.fromString(body.get("id").asText());
		assertThat(result.andReturn().getResponse().getHeader("Location")).isEqualTo(USERS + "/" + id);
		assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "email");
	}

	@Test
	void locationHeaderOfCreatedUserPointsToTheRetrievableUser() throws Exception {
		String email = uniqueEmail();
		String location = api().postJson(USERS, userBody("Bob", email))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");

		assertThat(location).isNotNull();
		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Bob"))
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void retrieveUserReturns200WithIdNameAndEmail() throws Exception {
		String email = uniqueEmail();
		UUID id = api().id(api().postJson(USERS, userBody("Carol Example", email)).andExpect(status().isCreated()));

		mockMvc.perform(get(USERS + "/" + id))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.name").value("Carol Example"))
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void emailIsStoredInLowerCase() throws Exception {
		String lower = uniqueEmail();
		String mixed = "UsEr-" + lower.substring("user-".length()).toUpperCase();

		ResultActions created = api().postJson(USERS, userBody("Mixed Case", mixed))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(lower));
		UUID id = api().id(created);

		mockMvc.perform(get(USERS + "/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(lower));
	}

	@Test
	void nameWithExactlyMaxLengthIsAccepted() throws Exception {
		api().postJson(USERS, userBody("n".repeat(100), uniqueEmail()))
				.andExpect(status().isCreated());
	}

	@Test
	void emailWithExactlyMaxLengthIsAccepted() throws Exception {
		String email = emailOfLength(254);
		assertThat(email).hasSize(254);

		api().postJson(USERS, userBody("Long Email", email))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email));
	}

	// ---------------------------------------------------------------- not found / malformed path

	@Test
	void retrieveUnknownUserReturns404UserNotFound() throws Exception {
		mockMvc.perform(get(USERS + "/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "user-not-found"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void retrieveUserWithMalformedUuidReturns400MalformedRequest() throws Exception {
		mockMvc.perform(get(USERS + "/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "malformed-request"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	// ---------------------------------------------------------------- validation

	@Test
	void blankNameReturns400ValidationErrorOnName() throws Exception {
		api().postJson(USERS, userBody("", uniqueEmail()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
	}

	@Test
	void whitespaceOnlyNameReturns400ValidationErrorOnName() throws Exception {
		api().postJson(USERS, userBody("   ", uniqueEmail()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
	}

	@Test
	void missingNameReturns400ValidationErrorOnName() throws Exception {
		api().postJson(USERS, "{\"email\":\"" + uniqueEmail() + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
	}

	@Test
	void missingEmailReturns400ValidationErrorOnEmail() throws Exception {
		api().postJson(USERS, "{\"name\":\"No Email\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
	}

	@Test
	void invalidEmailFormatReturns400ValidationErrorOnEmail() throws Exception {
		api().postJson(USERS, userBody("Bad Email", "not-an-email"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
	}

	@Test
	void nameLongerThan100CharactersReturns400ValidationErrorOnName() throws Exception {
		api().postJson(USERS, userBody("n".repeat(101), uniqueEmail()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
	}

	@Test
	void emailLongerThan254CharactersReturns400ValidationErrorOnEmail() throws Exception {
		String email = emailOfLength(255);
		assertThat(email).hasSize(255);

		api().postJson(USERS, userBody("Too Long Email", email))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
	}

	@Test
	void validationErrorReportsEveryViolatedField() throws Exception {
		api().postJson(USERS, "{\"name\":\"\",\"email\":\"nope\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "validation-error"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
				.andExpect(jsonPath("$.errors[*].message").isNotEmpty());
	}

	// ---------------------------------------------------------------- duplicates

	@Test
	void duplicateEmailReturns409EmailAlreadyRegistered() throws Exception {
		String email = uniqueEmail();
		api().postJson(USERS, userBody("First", email)).andExpect(status().isCreated());

		api().postJson(USERS, userBody("Second", email))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "email-already-registered"))
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void duplicateEmailIsDetectedWhenOnlyTheCaseDiffers() throws Exception {
		String email = uniqueEmail();
		api().postJson(USERS, userBody("First", email)).andExpect(status().isCreated());

		api().postJson(USERS, userBody("Second", email.toUpperCase()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "email-already-registered"));
	}

	@Test
	void duplicateEmailDoesNotChangeTheExistingUser() throws Exception {
		String email = uniqueEmail();
		UUID first = api().id(api().postJson(USERS, userBody("First", email)).andExpect(status().isCreated()));

		api().postJson(USERS, userBody("Second", email)).andExpect(status().isConflict());

		mockMvc.perform(get(USERS + "/" + first))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("First"))
				.andExpect(jsonPath("$.email").value(email));
	}

	// ---------------------------------------------------------------- unreadable input

	@Test
	void unknownJsonPropertyReturns400MalformedRequest() throws Exception {
		api().postJson(USERS, "{\"name\":\"Alice\",\"email\":\"" + uniqueEmail() + "\",\"nickname\":\"al\"}")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "malformed-request"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void invalidJsonBodyReturns400MalformedRequest() throws Exception {
		api().postJson(USERS, "{\"name\": \"Alice\", \"email\": ")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "malformed-request"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void wrongJsonTypeReturns400MalformedRequest() throws Exception {
		api().postJson(USERS, "{\"name\":[\"Alice\"],\"email\":\"" + uniqueEmail() + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "malformed-request"));
	}

	@Test
	void emptyBodyReturns400() throws Exception {
		mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	// ---------------------------------------------------------------- framework errors

	@Test
	void wrongContentTypeReturns415AboutBlankProblem() throws Exception {
		mockMvc.perform(post(USERS).contentType(MediaType.TEXT_PLAIN)
				.content("{\"name\":\"Alice\",\"email\":\"" + uniqueEmail() + "\"}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.status").value(415));
	}

	// ---------------------------------------------------------------- problem structure

	@Test
	void notFoundResponseCarriesTheProblemDetailFields() throws Exception {
		UUID unknown = UUID.randomUUID();

		mockMvc.perform(get(USERS + "/" + unknown))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Content-Type", startsWith("application/problem+json")))
				.andExpect(jsonPath("$.type").value(PROBLEM_PREFIX + "user-not-found"))
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.instance").value(USERS + "/" + unknown));
	}

	@Test
	void validationErrorResponseIsProblemJsonWithErrorsArray() throws Exception {
		api().postJson(USERS, userBody("", uniqueEmail()))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", startsWith("application/problem+json")))
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.instance").value(USERS))
				.andExpect(jsonPath("$.errors").isArray())
				.andExpect(jsonPath("$.errors[0].field").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].message").isNotEmpty());
	}

	// ---------------------------------------------------------------- helpers

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String userBody(String name, String email) {
		return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
	}

	/**
	 * Builds a syntactically valid, unique, lower-case email of exactly {@code length} characters
	 * (at least 200): a 64-character local part and a domain made of two 63-character labels, a
	 * variable label and the {@code com} top-level domain, so no label exceeds 63 characters.
	 */
	private static String emailOfLength(int length) {
		String local = ("u" + UUID.randomUUID().toString().replace("-", "") + "a".repeat(64)).substring(0, 64);
		String fixedLabel = "b".repeat(63);
		// length = local + "@" + fixed + "." + fixed + "." + variable + ".com"
		int variableLabel = length - local.length() - 1 - 2 * (fixedLabel.length() + 1) - ".com".length();
		assertThat(variableLabel).isBetween(1, 63);
		return local + "@" + fixedLabel + "." + fixedLabel + "." + "c".repeat(variableLabel) + ".com";
	}

}
