package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Meeting booking and listing: contract rules, error precedence and the concurrency invariant. */
class MeetingApiTest extends IntegrationTest {

	private static final String PROBLEM = "urn:mini-dood:problem:";
	private static final String DAY_START = "2030-01-01T00:00:00Z";
	private static final String DAY_END = "2030-01-02T00:00:00Z";

	// ---------------------------------------------------------------- booking: success

	@Test
	void bookingAFreeSlotReturns201WithLocationAndAllContractFields() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		ResultActions result = postMeeting(organizer, "{\"slotId\":\"" + slot
				+ "\",\"title\":\"Sprint planning\",\"description\":\"Plan the next sprint.\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Sprint planning"))
				.andExpect(jsonPath("$.description").value("Plan the next sprint."))
				.andExpect(jsonPath("$.organizerId").value(organizer.toString()))
				.andExpect(jsonPath("$.slotId").value(slot.toString()))
				.andExpect(jsonPath("$.participantIds", hasSize(0)))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		JsonNode meeting = api().json(result);
		String meetingId = meeting.get("id").asText();
		assertThat(UUID.fromString(meetingId)).isNotNull();
		result.andExpect(header().string("Location", "/api/v1/users/" + organizer + "/meetings/" + meetingId));
		assertThat(Instant.parse(meeting.get("startTime").asText())).isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
		assertThat(Instant.parse(meeting.get("endTime").asText())).isEqualTo(Instant.parse("2030-01-01T10:00:00Z"));
		assertThat(meeting.get("startTime").asText()).endsWith("Z");
		assertThat(meeting.get("endTime").asText()).endsWith("Z");
		assertThat(Instant.parse(meeting.get("createdAt").asText())).isNotNull();
		assertThat(meeting.get("createdAt").asText()).endsWith("Z");
	}

	@Test
	void meetingTimesAreCopiedFromTheSlotEvenWhenTheSlotWasCreatedWithAnOffset() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T10:00:00+01:00", "2030-01-01T11:30:00+01:00");

		JsonNode meeting = api().json(book(organizer, slot, "Offset slot").andExpect(status().isCreated()));

		assertThat(Instant.parse(meeting.get("startTime").asText())).isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
		assertThat(Instant.parse(meeting.get("endTime").asText())).isEqualTo(Instant.parse("2030-01-01T10:30:00Z"));
		assertThat(meeting.get("startTime").asText()).endsWith("Z");
	}

	@Test
	void descriptionFieldIsPresentAndNullWhenOmitted() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		JsonNode meeting = api().json(
				postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"No description\"}")
						.andExpect(status().isCreated()));

		assertThat(meeting.has("description")).as("description field must be present").isTrue();
		assertThat(meeting.get("description").isNull()).isTrue();
	}

	@Test
	void descriptionFieldIsPresentAndNullWhenExplicitlyNull() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		JsonNode meeting = api().json(postMeeting(organizer,
				"{\"slotId\":\"" + slot + "\",\"title\":\"Null description\",\"description\":null}")
				.andExpect(status().isCreated()));

		assertThat(meeting.has("description")).isTrue();
		assertThat(meeting.get("description").isNull()).isTrue();
	}

	@Test
	void descriptionNullIsAlsoPresentWhenRetrievingAndListingTheMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(
				postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"No description\"}")
						.andExpect(status().isCreated()));

		JsonNode fetched = api().json(getMeeting(organizer, meetingId).andExpect(status().isOk()));
		JsonNode listed = api().json(listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk()));

		assertThat(fetched.has("description")).isTrue();
		assertThat(fetched.get("description").isNull()).isTrue();
		assertThat(listed.get(0).has("description")).isTrue();
		assertThat(listed.get(0).get("description").isNull()).isTrue();
	}

	@Test
	void participantIdsAreReturnedAsLowerCaseStringsSortedAscending() throws Exception {
		UUID organizer = api().createUser();
		List<UUID> participants = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			participants.add(api().createUser());
		}
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		List<String> expected = participants.stream().map(UUID::toString).sorted().toList();

		// Send them in descending order and in upper case; the response must still be lower case and ascending.
		List<String> sent = new ArrayList<>(expected);
		sent.sort(Comparator.reverseOrder());
		String ids = String.join(",", sent.stream().map(id -> "\"" + id.toUpperCase() + "\"").toList());
		JsonNode created = api().json(postMeeting(organizer,
				"{\"slotId\":\"" + slot + "\",\"title\":\"Sorted\",\"participantIds\":[" + ids + "]}")
				.andExpect(status().isCreated()));

		assertThat(texts(created.get("participantIds"))).containsExactlyElementsOf(expected);
		UUID meetingId = UUID.fromString(created.get("id").asText());
		JsonNode fetched = api().json(getMeeting(organizer, meetingId).andExpect(status().isOk()));
		assertThat(texts(fetched.get("participantIds"))).containsExactlyElementsOf(expected);
		JsonNode listed = api().json(listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk()));
		assertThat(texts(listed.get(0).get("participantIds"))).containsExactlyElementsOf(expected);
	}

	@Test
	void slotIsBusyAfterBookingAndAvailabilityShowsBusy() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		getSlot(organizer, slot).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FREE"));

		book(organizer, slot, "Book me").andExpect(status().isCreated());

		getSlot(organizer, slot).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BUSY"));
		api().getAvailability(organizer, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].status").value("BUSY"))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T09:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T10:00:00Z"));
	}

	@Test
	void participantsSlotsAreNeitherCheckedNorReserved() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID organizerSlot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID participantFreeSlot = api().createSlot(participant, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID participantBusySlot = api().createSlot(participant, "2030-01-01T11:00:00Z", "2030-01-01T12:00:00Z");
		api().patchStatus(participant, participantBusySlot, "BUSY").andExpect(status().isOk());

		api().postMeeting(organizer, organizerSlot, "Overlapping participant", participant)
				.andExpect(status().isCreated());

		getSlot(participant, participantFreeSlot).andExpect(jsonPath("$.status").value("FREE"));
		getSlot(participant, participantBusySlot).andExpect(jsonPath("$.status").value("BUSY"));
	}

	// ---------------------------------------------------------------- booking: conflicts

	@Test
	void bookingABusySlotReturns409SlotNotFree() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		api().patchStatus(organizer, slot, "BUSY").andExpect(status().isOk());

		book(organizer, slot, "Busy").andExpect(status().isConflict()).andExpect(problem(409, "slot-not-free"));

		listMeetings(organizer, DAY_START, DAY_END).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void bookingTheSameSlotTwiceReturns409SlotNotFreeAndKeepsOneMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		UUID first = api().id(book(organizer, slot, "First").andExpect(status().isCreated()));
		book(organizer, slot, "Second").andExpect(status().isConflict()).andExpect(problem(409, "slot-not-free"));

		listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(first.toString()))
				.andExpect(jsonPath("$[0].title").value("First"));
	}

	@Test
	void slotOwnedByAnotherUserReturns404SlotNotFoundAndStaysFree() throws Exception {
		UUID owner = api().createUser();
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(owner, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		book(organizer, slot, "Not yours").andExpect(status().isNotFound()).andExpect(problem(404, "slot-not-found"));

		getSlot(owner, slot).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FREE"));
		listMeetings(organizer, DAY_START, DAY_END).andExpect(jsonPath("$", hasSize(0)));
		listMeetings(owner, DAY_START, DAY_END).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void unknownSlotIdReturns404SlotNotFound() throws Exception {
		UUID organizer = api().createUser();

		book(organizer, UUID.randomUUID(), "Ghost slot").andExpect(status().isNotFound())
				.andExpect(problem(404, "slot-not-found"));
	}

	// ---------------------------------------------------------------- booking: 404 on users

	@Test
	void unknownOrganizerReturns404UserNotFound() throws Exception {
		UUID slotOwner = api().createUser();
		UUID slot = api().createSlot(slotOwner, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		book(UUID.randomUUID(), slot, "Unknown organizer").andExpect(status().isNotFound())
				.andExpect(problem(404, "user-not-found"));

		getSlot(slotOwner, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void unknownParticipantReturns404UserNotFoundAndSlotStaysFree() throws Exception {
		UUID organizer = api().createUser();
		UUID knownParticipant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		api().postMeeting(organizer, slot, "Ghost participant", knownParticipant, UUID.randomUUID())
				.andExpect(status().isNotFound()).andExpect(problem(404, "user-not-found"));

		getSlot(organizer, slot).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FREE"));
		listMeetings(organizer, DAY_START, DAY_END).andExpect(jsonPath("$", hasSize(0)));
		listMeetings(knownParticipant, DAY_START, DAY_END).andExpect(jsonPath("$", hasSize(0)));
		// Nothing was committed, so the slot can still be booked afterwards.
		api().postMeeting(organizer, slot, "Retry", knownParticipant).andExpect(status().isCreated());
	}

	// ---------------------------------------------------------------- booking: 400 on participantIds

	@Test
	void organizerInParticipantIdsReturns400ValidationErrorOnParticipantIds() throws Exception {
		UUID organizer = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		api().postMeeting(organizer, slot, "Self invite", other, organizer).andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("participantIds")));

		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void duplicateParticipantIdsReturn400ValidationErrorOnParticipantIds() throws Exception {
		UUID organizer = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		api().postMeeting(organizer, slot, "Twice", other, other).andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("participantIds")));

		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void duplicateParticipantIdsDifferingOnlyInCaseReturn400() throws Exception {
		UUID organizer = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"Case\",\"participantIds\":[\"" + other
				+ "\",\"" + other.toString().toUpperCase() + "\"]}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"));
	}

	@Test
	void nullEntryInParticipantIdsReturns400ValidationErrorOnParticipantIds() throws Exception {
		UUID organizer = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"Null entry\",\"participantIds\":[\"" + other
				+ "\",null]}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("participantIds")));

		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void malformedUuidInParticipantIdsReturns400MalformedRequest() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"Bad id\",\"participantIds\":[\"nope\"]}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"));
	}

	@Test
	void moreThanOneHundredParticipantsReturn400ValidationErrorOnParticipantIds() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			ids.add("\"" + UUID.randomUUID() + "\"");
		}

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"Crowd\",\"participantIds\":["
				+ String.join(",", ids) + "]}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("participantIds")));
	}

	@Test
	void participantIdsOmittedExplicitNullAndEmptyAllCreateAMeetingWithoutParticipants() throws Exception {
		UUID organizer = api().createUser();
		UUID omittedSlot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID nullSlot = api().createSlot(organizer, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		UUID emptySlot = api().createSlot(organizer, "2030-01-01T11:00:00Z", "2030-01-01T12:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + omittedSlot + "\",\"title\":\"Omitted\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.participantIds", hasSize(0)));
		postMeeting(organizer, "{\"slotId\":\"" + nullSlot + "\",\"title\":\"Null\",\"participantIds\":null}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.participantIds", hasSize(0)));
		postMeeting(organizer, "{\"slotId\":\"" + emptySlot + "\",\"title\":\"Empty\",\"participantIds\":[]}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.participantIds", hasSize(0)));
	}

	// ---------------------------------------------------------------- booking: 400 on other fields

	@Test
	void blankTitleReturns400ValidationErrorOnTitle() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		for (String title : List.of("", "   ")) {
			postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"" + title + "\"}")
					.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
					.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
		}
		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void missingTitleReturns400ValidationErrorOnTitle() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\"}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
	}

	@Test
	void nullTitleReturns400ValidationErrorOnTitle() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":null}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
	}

	@Test
	void titleLongerThan200CharactersReturns400AndExactly200IsAccepted() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"" + "t".repeat(201) + "\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"" + "t".repeat(200) + "\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("t".repeat(200)));
	}

	@Test
	void descriptionLongerThan2000CharactersReturns400AndExactly2000IsAccepted() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer,
				"{\"slotId\":\"" + slot + "\",\"title\":\"Long\",\"description\":\"" + "d".repeat(2001) + "\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("description")));
		postMeeting(organizer,
				"{\"slotId\":\"" + slot + "\",\"title\":\"Long\",\"description\":\"" + "d".repeat(2000) + "\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("d".repeat(2000)));
	}

	@Test
	void missingSlotIdReturns400ValidationErrorOnSlotId() throws Exception {
		UUID organizer = api().createUser();

		postMeeting(organizer, "{\"title\":\"No slot\"}").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("slotId")));
	}

	@Test
	void malformedSlotIdReturns400MalformedRequest() throws Exception {
		UUID organizer = api().createUser();

		postMeeting(organizer, "{\"slotId\":\"not-a-uuid\",\"title\":\"Bad slot\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void unknownBodyPropertyAndInvalidJsonReturn400MalformedRequest() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");

		postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"Extra\",\"color\":\"red\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"));
		postMeeting(organizer, "{\"slotId\":").andExpect(status().isBadRequest())
				.andExpect(problem(400, "malformed-request"));
		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void malformedOrganizerIdInPathReturns400MalformedRequest() throws Exception {
		api().postJson("/api/v1/users/not-a-uuid/meetings",
				"{\"slotId\":\"" + UUID.randomUUID() + "\",\"title\":\"Bad path\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"));
	}

	// ---------------------------------------------------------------- booking: precedence

	@Test
	void busySlotWithUnknownParticipantReturns404NotConflict() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		api().patchStatus(organizer, slot, "BUSY").andExpect(status().isOk());

		api().postMeeting(organizer, slot, "Precedence", UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(problem(404, "user-not-found"));
	}

	@Test
	void invalidBodyForUnknownOrganizerReturns400NotNotFound() throws Exception {
		postMeeting(UUID.randomUUID(), "{\"slotId\":\"" + UUID.randomUUID() + "\",\"title\":\"\"}")
				.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
	}

	@Test
	void organizerInParticipantsForUnknownOrganizerReturns400NotNotFound() throws Exception {
		UUID organizer = UUID.randomUUID();

		api().postMeeting(organizer, UUID.randomUUID(), "Self", organizer).andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"));
	}

	@Test
	void unknownOrganizerWinsOverUnknownSlotAndUnknownParticipant() throws Exception {
		api().postMeeting(UUID.randomUUID(), UUID.randomUUID(), "All unknown", UUID.randomUUID())
				.andExpect(status().isNotFound()).andExpect(problem(404, "user-not-found"));
	}

	@Test
	void unknownSlotWinsOverUnknownParticipant() throws Exception {
		UUID organizer = api().createUser();

		api().postMeeting(organizer, UUID.randomUUID(), "Slot first", UUID.randomUUID())
				.andExpect(status().isNotFound()).andExpect(problem(404, "slot-not-found"));
	}

	// ---------------------------------------------------------------- listing

	@Test
	void organizerSeesTheirMeetings() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(book(organizer, slot, "Mine").andExpect(status().isCreated()));

		listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(meetingId.toString()))
				.andExpect(jsonPath("$[0].organizerId").value(organizer.toString()));
	}

	@Test
	void participantSeesTheMeetingToo() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(api().postMeeting(organizer, slot, "Shared", participant)
				.andExpect(status().isCreated()));

		listMeetings(participant, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(meetingId.toString()))
				.andExpect(jsonPath("$[0].organizerId").value(organizer.toString()))
				.andExpect(jsonPath("$[0].participantIds", contains(participant.toString())));
	}

	@Test
	void userWhoIsNeitherOrganizerNorParticipantSeesNoMeetings() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID bystander = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		api().postMeeting(organizer, slot, "Private", participant).andExpect(status().isCreated());

		listMeetings(bystander, DAY_START, DAY_END).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
	}

	@Test
	void listingIncludesMeetingsAsOrganizerAndAsParticipantSortedByStartTime() throws Exception {
		UUID user = api().createUser();
		UUID other = api().createUser();
		UUID ownSlot = api().createSlot(user, "2030-01-01T14:00:00Z", "2030-01-01T15:00:00Z");
		UUID otherSlot = api().createSlot(other, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID ownMeeting = api().id(book(user, ownSlot, "Own").andExpect(status().isCreated()));
		UUID invitedMeeting = api().id(api().postMeeting(other, otherSlot, "Invited", user)
				.andExpect(status().isCreated()));

		listMeetings(user, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].id", contains(invitedMeeting.toString(), ownMeeting.toString())));
	}

	@Test
	void listingIsSortedByStartTimeAscendingRegardlessOfBookingOrder() throws Exception {
		UUID organizer = api().createUser();
		UUID late = api().createSlot(organizer, "2030-01-01T15:00:00Z", "2030-01-01T16:00:00Z");
		UUID early = api().createSlot(organizer, "2030-01-01T08:00:00Z", "2030-01-01T09:00:00Z");
		UUID middle = api().createSlot(organizer, "2030-01-01T11:00:00Z", "2030-01-01T12:00:00Z");
		UUID lateMeeting = api().id(book(organizer, late, "Late").andExpect(status().isCreated()));
		UUID earlyMeeting = api().id(book(organizer, early, "Early").andExpect(status().isCreated()));
		UUID middleMeeting = api().id(book(organizer, middle, "Middle").andExpect(status().isCreated()));

		listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id",
						contains(earlyMeeting.toString(), middleMeeting.toString(), lateMeeting.toString())));
	}

	@Test
	void meetingEndingExactlyAtFromIsExcluded() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Ends at from").andExpect(status().isCreated());

		listMeetings(organizer, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z").andExpect(status().isOk())
				.andExpect(jsonPath("$", empty()));
	}

	@Test
	void meetingStartingExactlyAtToIsExcluded() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Starts at to").andExpect(status().isCreated());

		listMeetings(organizer, "2030-01-01T08:00:00Z", "2030-01-01T09:00:00Z").andExpect(status().isOk())
				.andExpect(jsonPath("$", empty()));
	}

	@Test
	void meetingOverlappingTheRangeByOneMicrosecondIsIncluded() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Just inside").andExpect(status().isCreated());

		listMeetings(organizer, "2030-01-01T09:59:59.999999Z", "2030-01-01T11:00:00Z").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		listMeetings(organizer, "2030-01-01T08:00:00Z", "2030-01-01T09:00:00.000001Z").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void meetingStraddlingTheRangeIsIncludedWholeAndNotClipped() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Straddle").andExpect(status().isCreated());

		for (String[] range : new String[][] { { "2030-01-01T09:30:00Z", "2030-01-01T09:45:00Z" },
				{ "2030-01-01T08:00:00Z", "2030-01-01T09:30:00Z" },
				{ "2030-01-01T09:30:00Z", "2030-01-01T12:00:00Z" } }) {
			JsonNode listed = api().json(listMeetings(organizer, range[0], range[1]).andExpect(status().isOk()));
			assertThat(listed).hasSize(1);
			assertThat(Instant.parse(listed.get(0).get("startTime").asText()))
					.isEqualTo(Instant.parse("2030-01-01T09:00:00Z"));
			assertThat(Instant.parse(listed.get(0).get("endTime").asText()))
					.isEqualTo(Instant.parse("2030-01-01T10:00:00Z"));
		}
	}

	@Test
	void listingAcceptsTimestampsWithAnOffset() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Offset query").andExpect(status().isCreated());

		// [10:00+01:00, 11:00+01:00) is [09:00Z, 10:00Z): contains the meeting.
		listMeetings(organizer, "2030-01-01T10:00:00+01:00", "2030-01-01T11:00:00+01:00").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		// [09:00+02:00, 11:00+02:00) is [07:00Z, 09:00Z): ends exactly at the meeting start, so it is excluded.
		listMeetings(organizer, "2030-01-01T09:00:00+02:00", "2030-01-01T11:00:00+02:00").andExpect(status().isOk())
				.andExpect(jsonPath("$", empty()));
	}

	@Test
	void listingWithFromEqualToToReturns400ValidationErrorOnTo() throws Exception {
		UUID organizer = api().createUser();

		listMeetings(organizer, "2030-01-01T09:00:00Z", "2030-01-01T09:00:00Z").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("to")));
	}

	@Test
	void listingWithFromAfterToReturns400ValidationErrorOnTo() throws Exception {
		UUID organizer = api().createUser();

		listMeetings(organizer, "2030-01-02T09:00:00Z", "2030-01-01T09:00:00Z").andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("to")));
	}

	@Test
	void listingWithoutFromReturns400ValidationErrorOnFrom() throws Exception {
		UUID organizer = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + organizer + "/meetings").param("to", DAY_END))
				.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("from")));
	}

	@Test
	void listingWithoutToReturns400ValidationErrorOnTo() throws Exception {
		UUID organizer = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + organizer + "/meetings").param("from", DAY_START))
				.andExpect(status().isBadRequest()).andExpect(problem(400, "validation-error"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("to")));
	}

	@Test
	void listingWithMalformedTimestampReturns400MalformedRequest() throws Exception {
		UUID organizer = api().createUser();

		listMeetings(organizer, "yesterday", DAY_END).andExpect(status().isBadRequest())
				.andExpect(problem(400, "malformed-request"));
		listMeetings(organizer, "2030-01-01T09:00:00", DAY_END).andExpect(status().isBadRequest())
				.andExpect(problem(400, "malformed-request"));
	}

	@Test
	void listingForUnknownUserReturns404UserNotFound() throws Exception {
		listMeetings(UUID.randomUUID(), DAY_START, DAY_END).andExpect(status().isNotFound())
				.andExpect(problem(404, "user-not-found"));
	}

	@Test
	void listingWithInvalidRangeForUnknownUserReturns400BeforeNotFound() throws Exception {
		listMeetings(UUID.randomUUID(), DAY_END, DAY_START).andExpect(status().isBadRequest())
				.andExpect(problem(400, "validation-error"));
	}

	@Test
	void listingForMalformedUserIdReturns400MalformedRequest() throws Exception {
		mockMvc.perform(get("/api/v1/users/not-a-uuid/meetings").param("from", DAY_START).param("to", DAY_END))
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"));
	}

	@Test
	void listingWithoutMeetingsReturnsAnEmptyArray() throws Exception {
		UUID user = api().createUser();

		listMeetings(user, DAY_START, DAY_END).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
	}

	// ---------------------------------------------------------------- get one

	@Test
	void organizerCanGetTheMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(api().postMeeting(organizer, slot, "Get me", participant)
				.andExpect(status().isCreated()));

		getMeeting(organizer, meetingId).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(meetingId.toString()))
				.andExpect(jsonPath("$.title").value("Get me"))
				.andExpect(jsonPath("$.organizerId").value(organizer.toString()))
				.andExpect(jsonPath("$.slotId").value(slot.toString()))
				.andExpect(jsonPath("$.startTime").value("2030-01-01T09:00:00Z"))
				.andExpect(jsonPath("$.endTime").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.participantIds", contains(participant.toString())));
	}

	@Test
	void participantCanGetTheMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(api().postMeeting(organizer, slot, "Invited", participant)
				.andExpect(status().isCreated()));

		getMeeting(participant, meetingId).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(meetingId.toString()))
				.andExpect(jsonPath("$.organizerId").value(organizer.toString()));
	}

	@Test
	void unrelatedUserGetsMeetingNotFound() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID bystander = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(api().postMeeting(organizer, slot, "Secret", participant)
				.andExpect(status().isCreated()));

		getMeeting(bystander, meetingId).andExpect(status().isNotFound()).andExpect(problem(404, "meeting-not-found"));
	}

	@Test
	void unknownUserGetsUserNotFoundEvenForAnExistingMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID meetingId = api().id(book(organizer, slot, "Exists").andExpect(status().isCreated()));

		getMeeting(UUID.randomUUID(), meetingId).andExpect(status().isNotFound())
				.andExpect(problem(404, "user-not-found"));
	}

	@Test
	void unknownMeetingReturns404MeetingNotFound() throws Exception {
		UUID user = api().createUser();

		getMeeting(user, UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(problem(404, "meeting-not-found"));
	}

	@Test
	void malformedMeetingIdReturns400MalformedRequest() throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user + "/meetings/not-a-uuid")).andExpect(status().isBadRequest())
				.andExpect(problem(400, "malformed-request"));
	}

	@Test
	void malformedMeetingIdForUnknownUserReturns400BeforeNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/meetings/not-a-uuid"))
				.andExpect(status().isBadRequest()).andExpect(problem(400, "malformed-request"));
	}

	// ---------------------------------------------------------------- booked slot is immutable

	@Test
	void slotBookedByAMeetingCannotBeFreedAgainThroughTheAvailabilityEndpoint() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		book(organizer, slot, "Booked").andExpect(status().isCreated());

		api().patchStatus(organizer, slot, "FREE").andExpect(status().isConflict())
				.andExpect(problem(409, "slot-booked"));
		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("BUSY"));
	}

	// ---------------------------------------------------------------- concurrency

	@Test
	void concurrentBookingsOfTheSameFreeSlotProduceExactlyOneMeeting() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		int attempts = 3;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		List<Integer> statuses = new ArrayList<>();
		List<String> conflictTypes = new ArrayList<>();
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				String title = "Attempt " + i;
				Callable<MockHttpServletResponse> booking = () -> {
					ready.countDown();
					go.await();
					return book(organizer, slot, title).andReturn().getResponse();
				};
				futures.add(executor.submit(booking));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get(60, TimeUnit.SECONDS);
				statuses.add(response.getStatus());
				if (response.getStatus() != 201) {
					conflictTypes.add(objectMapper.readTree(response.getContentAsString()).get("type").asText());
				}
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
		assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
		assertThat(conflictTypes).hasSize(attempts - 1).allMatch(type -> type.equals(PROBLEM + "slot-not-free"));
		listMeetings(organizer, DAY_START, DAY_END).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].slotId").value(slot.toString()));
		getSlot(organizer, slot).andExpect(jsonPath("$.status").value("BUSY"));
	}

	// ---------------------------------------------------------------- helpers

	private ResultActions postMeeting(UUID organizer, String json) throws Exception {
		return api().postJson("/api/v1/users/" + organizer + "/meetings", json);
	}

	private ResultActions book(UUID organizer, UUID slot, String title) throws Exception {
		return postMeeting(organizer, "{\"slotId\":\"" + slot + "\",\"title\":\"" + title + "\"}");
	}

	private ResultActions listMeetings(UUID userId, String from, String to) throws Exception {
		return mockMvc.perform(get("/api/v1/users/" + userId + "/meetings").param("from", from).param("to", to));
	}

	private ResultActions getMeeting(UUID userId, UUID meetingId) throws Exception {
		return mockMvc.perform(get("/api/v1/users/" + userId + "/meetings/" + meetingId));
	}

	private ResultActions getSlot(UUID userId, UUID slotId) throws Exception {
		return mockMvc.perform(get("/api/v1/users/" + userId + "/slots/" + slotId));
	}

	private static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}

	/** Asserts status, problem+json media type, the problem type and the mandatory fields of the contract. */
	private ResultMatcher problem(int status, String code) {
		return result -> {
			assertThat(result.getResponse().getStatus()).isEqualTo(status);
			assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
			assertThat(body.get("type").asText()).isEqualTo(PROBLEM + code);
			assertThat(body.get("status").asInt()).isEqualTo(status);
			assertThat(body.hasNonNull("title")).isTrue();
			assertThat(body.hasNonNull("detail")).isTrue();
			assertThat(body.hasNonNull("instance")).isTrue();
			if (code.equals("validation-error")) {
				assertThat(body.get("errors").isArray()).isTrue();
				assertThat(body.get("errors")).isNotEmpty();
			}
		};
	}

}
