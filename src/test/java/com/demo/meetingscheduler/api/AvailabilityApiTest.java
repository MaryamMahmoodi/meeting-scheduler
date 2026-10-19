package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** GET /api/v1/users/{id}/availability: intersecting slots, whole, unmerged, sorted, gaps never reported. */
class AvailabilityApiTest extends IntegrationTest {

	private static final String FROM = "2030-01-01T10:00:00Z";
	private static final String TO = "2030-01-01T12:00:00Z";

	// ---- response shape ----

	@Test
	void responseEchoesUserAndRangeInUtcAndExposesOnlyStartEndAndStatusPerSlot() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:30:00Z", "2030-01-01T11:00:00Z");

		ResultActions result = api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.userId").value(user.toString()))
				.andExpect(jsonPath("$.from").value(FROM))
				.andExpect(jsonPath("$.to").value(TO))
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:30:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T11:00:00Z"))
				.andExpect(jsonPath("$.slots[0].status").value("FREE"));

		JsonNode body = api().json(result);
		assertThat(fieldNames(body)).containsExactlyInAnyOrder("userId", "from", "to", "slots");
		assertThat(fieldNames(body.get("slots").get(0))).containsExactlyInAnyOrder("startTime", "endTime", "status");
	}

	@Test
	void returnsFreeAndBusySlots() throws Exception {
		UUID user = api().createUser();
		UUID busy = api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		api().createSlot(user, "2030-01-01T11:00:00Z", "2030-01-01T12:00:00Z");
		api().patchStatus(user, busy, "BUSY").andExpect(status().isOk());

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(2)))
				.andExpect(jsonPath("$.slots[0].status").value("BUSY"))
				.andExpect(jsonPath("$.slots[1].status").value("FREE"));
	}

	// ---- intersection semantics ----

	@Test
	void slotFullyInsideTheRangeIsIncluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:30:00Z", "2030-01-01T11:30:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:30:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T11:30:00Z"));
	}

	@Test
	void slotStartingBeforeFromAndEndingInsideIsIncludedWholeAndNotClipped() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T09:00:00Z", "2030-01-01T10:30:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T09:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T10:30:00Z"));
	}

	@Test
	void slotStartingInsideAndEndingAfterToIsIncludedWholeAndNotClipped() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T11:30:00Z", "2030-01-01T13:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T11:30:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T13:00:00Z"));
	}

	@Test
	void slotCoveringTheWholeRangeIsIncludedWhole() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T08:00:00Z", "2030-01-01T14:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T08:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T14:00:00Z"));
	}

	@Test
	void slotEndingExactlyAtFromIsExcluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T09:00:00Z", FROM);

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void slotStartingExactlyAtToIsExcluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, TO, "2030-01-01T13:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void slotEndingOneMicrosecondAfterFromIsIncluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00.000001Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T09:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T10:00:00.000001Z"));
	}

	@Test
	void slotStartingOneMicrosecondBeforeToIsIncluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T11:59:59.999999Z", "2030-01-01T13:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T11:59:59.999999Z"));
	}

	@Test
	void slotsEntirelyOutsideTheRangeAreExcluded() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T07:00:00Z", "2030-01-01T08:00:00Z");
		api().createSlot(user, "2030-01-01T13:00:00Z", "2030-01-01T14:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(0)));
	}

	// ---- gaps, merging, ordering, ownership ----

	@Test
	void gapBetweenSlotsIsNotReportedAndNothingIsSynthesized() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T10:30:00Z");
		api().createSlot(user, "2030-01-01T11:00:00Z", "2030-01-01T11:30:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(2)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T10:30:00Z"))
				.andExpect(jsonPath("$.slots[0].status").value("FREE"))
				.andExpect(jsonPath("$.slots[1].startTime").value("2030-01-01T11:00:00Z"))
				.andExpect(jsonPath("$.slots[1].endTime").value("2030-01-01T11:30:00Z"))
				.andExpect(jsonPath("$.slots[1].status").value("FREE"));
	}

	@Test
	void adjacentFreeSlotsAreNotMerged() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		api().createSlot(user, "2030-01-01T11:00:00Z", "2030-01-01T12:00:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(2)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T11:00:00Z"))
				.andExpect(jsonPath("$.slots[1].startTime").value("2030-01-01T11:00:00Z"))
				.andExpect(jsonPath("$.slots[1].endTime").value("2030-01-01T12:00:00Z"));
	}

	@Test
	void slotsAreSortedByStartTimeAscendingRegardlessOfCreationOrder() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T11:30:00Z", "2030-01-01T11:45:00Z");
		api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T10:15:00Z");
		api().createSlot(user, "2030-01-01T11:00:00Z", "2030-01-01T11:15:00Z");
		api().createSlot(user, "2030-01-01T10:30:00Z", "2030-01-01T10:45:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(4)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.slots[1].startTime").value("2030-01-01T10:30:00Z"))
				.andExpect(jsonPath("$.slots[2].startTime").value("2030-01-01T11:00:00Z"))
				.andExpect(jsonPath("$.slots[3].startTime").value("2030-01-01T11:30:00Z"));
	}

	@Test
	void slotsOfAnotherUserAreNeverIncluded() throws Exception {
		UUID user = api().createUser();
		UUID other = api().createUser();
		api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T10:30:00Z");
		api().createSlot(other, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		api().createSlot(other, "2030-01-01T11:00:00Z", "2030-01-01T11:30:00Z");

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(user.toString()))
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-01-01T10:30:00Z"));
		api().getAvailability(other, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(2)));
	}

	@Test
	void userWithoutSlotsGetsAnEmptySlotsArray() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(user.toString()))
				.andExpect(jsonPath("$.slots").isArray())
				.andExpect(jsonPath("$.slots", hasSize(0)));
	}

	// ---- reflecting changes ----

	@Test
	void slotTurnsBusyAfterAMeetingIsBookedOnIt() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		api().getAvailability(organizer, FROM, TO).andExpect(jsonPath("$.slots[0].status").value("FREE"));

		api().postMeeting(organizer, slot, "Planning", participant).andExpect(status().isCreated());

		api().getAvailability(organizer, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].status").value("BUSY"));
	}

	@Test
	void participantAvailabilityIsNotAffectedByABooking() throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");
		api().createSlot(participant, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");

		api().postMeeting(organizer, slot, "Planning", participant).andExpect(status().isCreated());

		api().getAvailability(participant, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].status").value("FREE"));
	}

	@Test
	void changingAvailabilityViaPatchIsReflectedInBothDirections() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");

		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk());
		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots[0].status").value("BUSY"));

		api().patchStatus(user, slot, "FREE").andExpect(status().isOk());
		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots[0].status").value("FREE"));
	}

	@Test
	void deletedSlotDisappearsFromAvailability() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T11:00:00Z", "2030-01-01T11:30:00Z");
		UUID deleted = api().createSlot(user, "2030-01-01T10:00:00Z", "2030-01-01T10:30:00Z");
		api().getAvailability(user, FROM, TO).andExpect(jsonPath("$.slots", hasSize(2)));

		mockMvc.perform(delete("/api/v1/users/" + user + "/slots/" + deleted)).andExpect(status().isNoContent());

		api().getAvailability(user, FROM, TO).andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T11:00:00Z"));
	}

	// ---- offsets ----

	@Test
	void fromAndToWithOffsetsAreEchoedInUtcAndSelectTheSameSlots() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:30:00Z", "2030-01-01T11:00:00Z");
		api().createSlot(user, "2030-01-01T12:00:00Z", "2030-01-01T12:30:00Z");

		// 11:00+01:00 = 10:00Z and 12:30+01:00 = 11:30Z
		api().getAvailability(user, "2030-01-01T11:00:00+01:00", "2030-01-01T12:30:00+01:00")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.to").value("2030-01-01T11:30:00Z"))
				.andExpect(jsonPath("$.slots", hasSize(1)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-01-01T10:30:00Z"));
	}

	@Test
	void fromAndToWithNegativeOffsetAreEchoedInUtc() throws Exception {
		UUID user = api().createUser();

		// 05:00-05:00 = 10:00Z and 07:00-05:00 = 12:00Z
		api().getAvailability(user, "2030-01-01T05:00:00-05:00", "2030-01-01T07:00:00-05:00")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value(FROM))
				.andExpect(jsonPath("$.to").value(TO));
	}

	@Test
	void percentEncodedPlusInOffsetIsAcceptedOverTheWire() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-01T10:30:00Z", "2030-01-01T11:00:00Z");

		// URI.create avoids URI-template re-encoding of the already percent-encoded plus sign
		mockMvc.perform(get(URI.create("/api/v1/users/" + user
				+ "/availability?from=2030-01-01T11:00:00%2B01:00&to=2030-01-01T13:00:00%2B01:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2030-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.to").value("2030-01-01T12:00:00Z"))
				.andExpect(jsonPath("$.slots", hasSize(1)));
	}

	// ---- validation (400) ----

	@Test
	void fromEqualToToIsAValidationErrorOnFieldTo() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, FROM, FROM).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("to"));
	}

	@Test
	void fromAfterToIsAValidationErrorOnFieldTo() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, TO, FROM).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("to"));
	}

	@Test
	void fromAndToEqualAfterConvertingOffsetsIsAValidationErrorOnFieldTo() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, "2030-01-01T11:00:00+01:00", "2030-01-01T10:00:00Z")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.errors[0].field").value("to"));
	}

	@Test
	void missingFromIsAValidationErrorOnFieldFrom() throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user + "/availability").param("to", TO))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("from"));
	}

	@Test
	void missingToIsAValidationErrorOnFieldTo() throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user + "/availability").param("from", FROM))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("to"));
	}

	@Test
	void missingBothFromAndToIsAValidationError() throws Exception {
		UUID user = api().createUser();

		mockMvc.perform(get("/api/v1/users/" + user + "/availability")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").isNotEmpty());
	}

	@Test
	void malformedFromIsAMalformedRequestWithoutErrorList() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, "not-a-date", TO).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:malformed-request"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void malformedToIsAMalformedRequest() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, FROM, "tomorrow").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:malformed-request"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void timestampWithoutOffsetIsAMalformedRequest() throws Exception {
		UUID user = api().createUser();

		api().getAvailability(user, "2030-01-01T10:00:00", TO).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:malformed-request"));
	}

	@Test
	void malformedUserIdIsAMalformedRequest() throws Exception {
		mockMvc.perform(get("/api/v1/users/not-a-uuid/availability").param("from", FROM).param("to", TO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:malformed-request"));
	}

	// ---- existence (404) and precedence ----

	@Test
	void unknownUserIsNotFound() throws Exception {
		api().getAvailability(UUID.randomUUID(), FROM, TO).andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:user-not-found"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void invalidRangeForAnUnknownUserIsStillA400ValidationError() throws Exception {
		api().getAvailability(UUID.randomUUID(), TO, FROM).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"))
				.andExpect(jsonPath("$.errors[0].field").value("to"));
		api().getAvailability(UUID.randomUUID(), FROM, FROM).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"));
	}

	@Test
	void malformedTimestampForAnUnknownUserIsStillA400() throws Exception {
		api().getAvailability(UUID.randomUUID(), "garbage", TO).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:malformed-request"));
	}

	@Test
	void missingParameterForAnUnknownUserIsStillA400() throws Exception {
		mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/availability").param("from", FROM))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:validation-error"));
	}

	// ---- larger range ----

	@Test
	void monthLongRangeWithinAYearOfSlotsReturnsOnlyTheIntersectingSlots() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, "2030-01-15T09:00:00Z", "2030-01-15T10:00:00Z"); // before
		api().createSlot(user, "2030-03-31T23:00:00Z", "2030-04-01T01:00:00Z"); // straddles the start
		api().createSlot(user, "2030-04-20T09:00:00Z", "2030-04-20T10:00:00Z"); // inside
		api().createSlot(user, "2030-04-30T23:30:00Z", "2030-05-01T00:30:00Z"); // straddles the end
		api().createSlot(user, "2030-05-01T00:30:00Z", "2030-05-01T01:00:00Z"); // after, adjacent to the straddler
		api().createSlot(user, "2030-09-10T09:00:00Z", "2030-09-10T10:00:00Z"); // after
		api().createSlot(user, "2030-12-24T09:00:00Z", "2030-12-24T10:00:00Z"); // after

		api().getAvailability(user, "2030-04-01T00:00:00Z", "2030-05-01T00:00:00Z").andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(3)))
				.andExpect(jsonPath("$.slots[0].startTime").value("2030-03-31T23:00:00Z"))
				.andExpect(jsonPath("$.slots[0].endTime").value("2030-04-01T01:00:00Z"))
				.andExpect(jsonPath("$.slots[1].startTime").value("2030-04-20T09:00:00Z"))
				.andExpect(jsonPath("$.slots[2].startTime").value("2030-04-30T23:30:00Z"))
				.andExpect(jsonPath("$.slots[2].endTime").value("2030-05-01T00:30:00Z"));

		JsonNode wholeYear = api().json(api().getAvailability(user, "2030-01-01T00:00:00Z", "2031-01-01T00:00:00Z")
				.andExpect(status().isOk()));
		assertThat(startTimes(wholeYear)).hasSize(7).isSorted();
	}

	// ---- helpers ----

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		Iterator<String> iterator = node.fieldNames();
		iterator.forEachRemaining(names::add);
		return names;
	}

	private static List<String> startTimes(JsonNode body) {
		List<String> starts = new ArrayList<>();
		body.get("slots").forEach(slot -> starts.add(slot.get("startTime").asText()));
		return starts;
	}

}
