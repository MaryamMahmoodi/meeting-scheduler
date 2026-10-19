package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.demo.meetingscheduler.support.Api;
import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Black-box tests of the time slot endpoints against the contract in docs/openapi.yaml. */
class TimeSlotApiTest extends IntegrationTest {

	private static final String PROBLEM = "urn:mini-dood:problem:";

	// ------------------------------------------------------------------ create

	@Test
	void createSlotReturns201WithLocationDefaultStatusFreeAndCreatedAt() throws Exception {
		UUID user = api().createUser();

		ResultActions result = api().postSlot(user, t("09:00"), t("10:00")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(user.toString()))
				.andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00")))
				.andExpect(jsonPath("$.status").value("FREE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		UUID slotId = api().id(result);
		result.andExpect(header().string("Location", "/api/v1/users/" + user + "/slots/" + slotId));
		getSlot(user, slotId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(slotId.toString()));
	}

	@Test
	void createSlotWithExplicitBusyStatusIsStoredAsBusy() throws Exception {
		UUID user = api().createUser();

		api().postJson(slotsUrl(user), body(t("09:00"), t("10:00"), "BUSY")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("BUSY"));
	}

	@Test
	void createSlotWithEndTimeEqualToStartTimeIsValidationErrorOnEndTime() throws Exception {
		UUID user = api().createUser();

		assertValidationError(api().postSlot(user, t("09:00"), t("09:00")), "endTime");
	}

	@Test
	void createSlotWithEndTimeBeforeStartTimeIsValidationErrorOnEndTime() throws Exception {
		UUID user = api().createUser();

		assertValidationError(api().postSlot(user, t("10:00"), t("09:00")), "endTime");
	}

	@Test
	void createSlotWithoutStartTimeIsValidationError() throws Exception {
		UUID user = api().createUser();

		assertValidationError(api().postJson(slotsUrl(user), "{\"endTime\":\"" + t("10:00") + "\"}"), "startTime");
	}

	@Test
	void createSlotWithoutEndTimeIsValidationError() throws Exception {
		UUID user = api().createUser();

		assertValidationError(api().postJson(slotsUrl(user), "{\"startTime\":\"" + t("09:00") + "\"}"), "endTime");
	}

	@Test
	void createSlotWithNullStartTimeIsValidationError() throws Exception {
		UUID user = api().createUser();

		assertValidationError(
				api().postJson(slotsUrl(user), "{\"startTime\":null,\"endTime\":\"" + t("10:00") + "\"}"),
				"startTime");
	}

	@Test
	void createSlotWithUnparsableTimestampIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(api().postSlot(user, "not-a-timestamp", t("10:00")));
	}

	@Test
	void createSlotWithTimestampWithoutOffsetIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(api().postSlot(user, "2030-01-01T09:00:00", "2030-01-01T10:00:00"));
	}

	@Test
	void createSlotWithInvalidStatusIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(api().postJson(slotsUrl(user), body(t("09:00"), t("10:00"), "BOOKED")));
	}

	@Test
	void createSlotWithUnknownPropertyIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(api().postJson(slotsUrl(user),
				"{\"startTime\":\"" + t("09:00") + "\",\"endTime\":\"" + t("10:00") + "\",\"foo\":1}"));
	}

	@Test
	void createSlotWithInvalidJsonIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(api().postJson(slotsUrl(user), "{not json"));
	}

	@Test
	void createSlotEchoesInputOffsetsInUtc() throws Exception {
		UUID user = api().createUser();

		api().postSlot(user, "2030-01-01T10:00:00+01:00", "2030-01-01T11:30:00+01:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("2030-01-01T09:00:00Z"))
				.andExpect(jsonPath("$.endTime").value("2030-01-01T10:30:00Z"));
	}

	@Test
	void createSlotTruncatesSubMicrosecondFractionsToMicros() throws Exception {
		UUID user = api().createUser();

		api().postSlot(user, "2030-01-01T09:00:00.123456789Z", "2030-01-01T10:00:00.987654321Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("2030-01-01T09:00:00.123456Z"))
				.andExpect(jsonPath("$.endTime").value("2030-01-01T10:00:00.987654Z"));
	}

	@Test
	void createSlotValidatesRangeAfterTruncatingToMicros() throws Exception {
		UUID user = api().createUser();

		// Both values truncate to 09:00:00.123456, so the range is empty.
		assertValidationError(api().postSlot(user, "2030-01-01T09:00:00.1234561Z", "2030-01-01T09:00:00.1234569Z"),
				"endTime");
	}

	@Test
	void createSlotChecksOverlapAfterTruncatingToMicros() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("09:00"), t("10:00"));

		// Starts 0.9 microseconds after 10:00:00, which truncates to exactly 10:00:00 -> adjacent, not overlapping.
		api().postSlot(user, "2030-01-01T10:00:00.0000009Z", t("11:00")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("2030-01-01T10:00:00Z"));
	}

	@Test
	void adjacentSlotBeforeAndAfterAnExistingSlotIsAccepted() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("11:00"));

		api().postSlot(user, t("09:00"), t("10:00")).andExpect(status().isCreated());
		api().postSlot(user, t("11:00"), t("12:00")).andExpect(status().isCreated());
	}

	@Test
	void identicalRangeOverlapIsRejectedWithSlotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("12:00"));

		assertProblem(api().postSlot(user, t("10:00"), t("12:00")), 409, "slot-overlap");
	}

	@Test
	void partialOverlapOnTheLeftIsRejectedWithSlotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("12:00"));

		assertProblem(api().postSlot(user, t("09:00"), t("10:30")), 409, "slot-overlap");
	}

	@Test
	void partialOverlapOnTheRightIsRejectedWithSlotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("12:00"));

		assertProblem(api().postSlot(user, t("11:30"), t("13:00")), 409, "slot-overlap");
	}

	@Test
	void containedRangeOverlapIsRejectedWithSlotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("12:00"));

		assertProblem(api().postSlot(user, t("10:30"), t("11:30")), 409, "slot-overlap");
	}

	@Test
	void containingRangeOverlapIsRejectedWithSlotOverlap() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("10:00"), t("12:00"));

		assertProblem(api().postSlot(user, t("09:00"), t("13:00")), 409, "slot-overlap");
	}

	@Test
	void overlapRuleAppliesPerUserOnly() throws Exception {
		UUID alice = api().createUser();
		UUID bob = api().createUser();
		api().createSlot(alice, t("10:00"), t("12:00"));

		api().postSlot(bob, t("10:00"), t("12:00")).andExpect(status().isCreated());
	}

	@Test
	void createSlotForUnknownUserIsUserNotFound() throws Exception {
		assertProblem(api().postSlot(UUID.randomUUID(), t("09:00"), t("10:00")), 404, "user-not-found");
	}

	@Test
	void createSlotWithMalformedUserIdIsMalformedRequest() throws Exception {
		assertMalformed(api().postJson("/api/v1/users/not-a-uuid/slots", Api.slotBody(t("09:00"), t("10:00"))));
	}

	// --------------------------------------------------------------------- get

	@Test
	void getSlotReturnsTheSlot() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		getSlot(user, slot).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(slot.toString()))
				.andExpect(jsonPath("$.userId").value(user.toString()))
				.andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00"))).andExpect(jsonPath("$.status").value("FREE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void getSlotOfAnotherUserIsSlotNotFound() throws Exception {
		UUID owner = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(owner, t("09:00"), t("10:00"));

		assertProblem(getSlot(other, slot), 404, "slot-not-found");
	}

	@Test
	void getUnknownSlotIsSlotNotFound() throws Exception {
		UUID user = api().createUser();

		assertProblem(getSlot(user, UUID.randomUUID()), 404, "slot-not-found");
	}

	@Test
	void getSlotForUnknownUserIsUserNotFound() throws Exception {
		assertProblem(getSlot(UUID.randomUUID(), UUID.randomUUID()), 404, "user-not-found");
	}

	@Test
	void getSlotWithMalformedSlotIdIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(mockMvc.perform(get(slotsUrl(user) + "/not-a-uuid")));
	}

	// ------------------------------------------------------------------ update

	@Test
	void updateSlotShiftsTheTimeRange() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		putSlot(user, slot, body(t("09:30"), t("10:30"), null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(slot.toString()))
				.andExpect(jsonPath("$.startTime").value(t("09:30")))
				.andExpect(jsonPath("$.endTime").value(t("10:30")));

		getSlot(user, slot).andExpect(jsonPath("$.startTime").value(t("09:30")))
				.andExpect(jsonPath("$.endTime").value(t("10:30")));
	}

	@Test
	void updateSlotToRangeOverlappingAnotherSlotIsSlotOverlap() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		api().createSlot(user, t("11:00"), t("12:00"));

		assertProblem(putSlot(user, slot, body(t("09:30"), t("11:30"), null)), 409, "slot-overlap");

		getSlot(user, slot).andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00")));
	}

	@Test
	void updateSlotToRangeOverlappingOnlyItselfIsAllowed() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		putSlot(user, slot, body(t("09:15"), t("09:45"), null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.startTime").value(t("09:15")))
				.andExpect(jsonPath("$.endTime").value(t("09:45")));
	}

	@Test
	void updateSlotToAdjacentRangeOfAnotherSlotIsAllowed() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		api().createSlot(user, t("11:00"), t("12:00"));

		putSlot(user, slot, body(t("10:00"), t("11:00"), null)).andExpect(status().isOk());
	}

	@Test
	void updateSlotWithoutStatusKeepsTheCurrentStatus() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk());

		putSlot(user, slot, body(t("09:30"), t("10:30"), null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BUSY"));
	}

	@Test
	void updateSlotWithStatusChangesTheStatus() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		putSlot(user, slot, body(t("09:00"), t("10:00"), "BUSY")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BUSY"));
		getSlot(user, slot).andExpect(jsonPath("$.status").value("BUSY"));

		putSlot(user, slot, body(t("09:00"), t("10:00"), "FREE")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void updateSlotWithEndTimeNotAfterStartTimeIsValidationErrorOnEndTime() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		assertValidationError(putSlot(user, slot, body(t("10:00"), t("10:00"), null)), "endTime");
		assertValidationError(putSlot(user, slot, body(t("11:00"), t("10:00"), null)), "endTime");

		getSlot(user, slot).andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00")));
	}

	@Test
	void updateSlotWithMissingStartTimeIsValidationError() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		assertValidationError(putSlot(user, slot, "{\"endTime\":\"" + t("10:00") + "\"}"), "startTime");
	}

	@Test
	void updateSlotWithInvalidStatusIsMalformedRequest() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		assertMalformed(putSlot(user, slot, body(t("09:00"), t("10:00"), "MAYBE")));
	}

	@Test
	void updateSlotOfAnotherUserIsSlotNotFoundAndLeavesTheSlotUnchanged() throws Exception {
		UUID owner = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(owner, t("09:00"), t("10:00"));

		assertProblem(putSlot(other, slot, body(t("12:00"), t("13:00"), "BUSY")), 404, "slot-not-found");

		getSlot(owner, slot).andExpect(status().isOk()).andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00"))).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void updateUnknownSlotIsSlotNotFound() throws Exception {
		UUID user = api().createUser();

		assertProblem(putSlot(user, UUID.randomUUID(), body(t("09:00"), t("10:00"), null)), 404, "slot-not-found");
	}

	@Test
	void updateSlotForUnknownUserIsUserNotFound() throws Exception {
		assertProblem(putSlot(UUID.randomUUID(), UUID.randomUUID(), body(t("09:00"), t("10:00"), null)), 404,
				"user-not-found");
	}

	// ------------------------------------------------------ availability PATCH

	@Test
	void patchAvailabilityChangesFreeToBusy() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BUSY"))
				.andExpect(jsonPath("$.startTime").value(t("09:00")))
				.andExpect(jsonPath("$.endTime").value(t("10:00")));
		getSlot(user, slot).andExpect(jsonPath("$.status").value("BUSY"));
	}

	@Test
	void patchAvailabilityChangesBusyToFree() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk());

		api().patchStatus(user, slot, "FREE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FREE"));
		getSlot(user, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void patchAvailabilityWithTheCurrentStatusIsANoOpReturning200() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		api().patchStatus(user, slot, "FREE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FREE"))
				.andExpect(jsonPath("$.startTime").value(t("09:00")));
		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk());
		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BUSY"));
	}

	@Test
	void patchAvailabilityWithInvalidStatusIsMalformedRequest() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		assertMalformed(api().patchStatus(user, slot, "BOOKED"));
		getSlot(user, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void patchAvailabilityWithoutStatusIsValidationError() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		assertValidationError(patchRaw(user, slot, "{}"), "status");
	}

	@Test
	void patchAvailabilityOfAnotherUsersSlotIsSlotNotFound() throws Exception {
		UUID owner = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(owner, t("09:00"), t("10:00"));

		assertProblem(api().patchStatus(other, slot, "BUSY"), 404, "slot-not-found");
		getSlot(owner, slot).andExpect(jsonPath("$.status").value("FREE"));
	}

	@Test
	void patchAvailabilityOfUnknownUserIsUserNotFound() throws Exception {
		assertProblem(api().patchStatus(UUID.randomUUID(), UUID.randomUUID(), "BUSY"), 404, "user-not-found");
	}

	// ------------------------------------------------------------------ delete

	@Test
	void deleteSlotReturns204AndTheSlotIsGone() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		deleteSlot(user, slot).andExpect(status().isNoContent());

		assertProblem(getSlot(user, slot), 404, "slot-not-found");
	}

	@Test
	void deletingASlotFreesItsTimeRangeForANewSlot() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		assertProblem(api().postSlot(user, t("09:00"), t("10:00")), 409, "slot-overlap");

		deleteSlot(user, slot).andExpect(status().isNoContent());

		api().postSlot(user, t("09:00"), t("10:00")).andExpect(status().isCreated());
	}

	@Test
	void deleteSlotOfAnotherUserIsSlotNotFoundAndTheSlotStillExists() throws Exception {
		UUID owner = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(owner, t("09:00"), t("10:00"));

		assertProblem(deleteSlot(other, slot), 404, "slot-not-found");

		getSlot(owner, slot).andExpect(status().isOk());
	}

	@Test
	void deleteUnknownSlotIsSlotNotFound() throws Exception {
		UUID user = api().createUser();

		assertProblem(deleteSlot(user, UUID.randomUUID()), 404, "slot-not-found");
	}

	@Test
	void deleteSlotForUnknownUserIsUserNotFound() throws Exception {
		assertProblem(deleteSlot(UUID.randomUUID(), UUID.randomUUID()), 404, "user-not-found");
	}

	// ------------------------------------------------------------ booked slots

	@Test
	void putOnBookedSlotIsSlotBookedEvenWithIdenticalValuesAndSlotIsUnchanged() throws Exception {
		UUID user = api().createUser();
		UUID slot = bookedSlot(user, t("09:00"), t("10:00"));

		assertProblem(putSlot(user, slot, body(t("09:00"), t("10:00"), "BUSY")), 409, "slot-booked");
		assertProblem(putSlot(user, slot, body(t("09:00"), t("10:00"), null)), 409, "slot-booked");
		assertProblem(putSlot(user, slot, body(t("11:00"), t("12:00"), "FREE")), 409, "slot-booked");

		assertSlotUnchanged(user, slot, t("09:00"), t("10:00"), "BUSY");
	}

	@Test
	void patchOnBookedSlotIsSlotBookedEvenWhenStatusIsUnchangedAndSlotIsUnchanged() throws Exception {
		UUID user = api().createUser();
		UUID slot = bookedSlot(user, t("09:00"), t("10:00"));

		assertProblem(api().patchStatus(user, slot, "BUSY"), 409, "slot-booked");
		assertProblem(api().patchStatus(user, slot, "FREE"), 409, "slot-booked");

		assertSlotUnchanged(user, slot, t("09:00"), t("10:00"), "BUSY");
	}

	@Test
	void deleteOnBookedSlotIsSlotBookedAndSlotIsUnchanged() throws Exception {
		UUID user = api().createUser();
		UUID slot = bookedSlot(user, t("09:00"), t("10:00"));

		assertProblem(deleteSlot(user, slot), 409, "slot-booked");

		assertSlotUnchanged(user, slot, t("09:00"), t("10:00"), "BUSY");
	}

	@Test
	void updatingBookedSlotToRangeAlsoOverlappingAnotherSlotIsSlotBookedNotSlotOverlap() throws Exception {
		UUID user = api().createUser();
		UUID slot = bookedSlot(user, t("09:00"), t("10:00"));
		api().createSlot(user, t("11:00"), t("12:00"));

		assertProblem(putSlot(user, slot, body(t("10:30"), t("11:30"), null)), 409, "slot-booked");

		assertSlotUnchanged(user, slot, t("09:00"), t("10:00"), "BUSY");
	}

	@Test
	void unbookedBusySlotCanStillBeChangedAndDeleted() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));
		api().patchStatus(user, slot, "BUSY").andExpect(status().isOk());

		putSlot(user, slot, body(t("09:30"), t("10:30"), null)).andExpect(status().isOk());
		deleteSlot(user, slot).andExpect(status().isNoContent());
	}

	// -------------------------------------------------------------- precedence

	@Test
	void invalidBodyForUnknownUserIsValidationErrorNotUserNotFound() throws Exception {
		UUID unknownUser = UUID.randomUUID();

		assertValidationError(api().postSlot(unknownUser, t("10:00"), t("09:00")), "endTime");
		assertMalformed(api().postJson(slotsUrl(unknownUser), body(t("09:00"), t("10:00"), "NOPE")));
	}

	@Test
	void invalidUpdateBodyForUnknownUserIsValidationErrorNotUserNotFound() throws Exception {
		assertValidationError(putSlot(UUID.randomUUID(), UUID.randomUUID(), body(t("10:00"), t("09:00"), null)),
				"endTime");
	}

	@Test
	void invalidUpdateBodyForUnknownSlotIsValidationErrorNotSlotNotFound() throws Exception {
		UUID user = api().createUser();

		assertValidationError(putSlot(user, UUID.randomUUID(), body(t("10:00"), t("09:00"), null)), "endTime");
	}

	@Test
	void unknownUserTakesPrecedenceOverUnknownSlot() throws Exception {
		assertProblem(putSlot(UUID.randomUUID(), UUID.randomUUID(), body(t("09:00"), t("10:00"), null)), 404,
				"user-not-found");
	}

	// -------------------------------------------------------------------- list

	@Test
	void listReturnsSlotsStraddlingTheBoundariesWholeAndUnclipped() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("09:00"), t("10:00"));
		api().createSlot(user, t("10:00"), t("11:00"));
		api().createSlot(user, t("11:00"), t("12:00"));

		JsonNode slots = api().json(listSlots(user, t("09:30"), t("11:30")).andExpect(status().isOk()));

		assertThat(slots).hasSize(3);
		assertThat(slots.get(0).get("startTime").asText()).isEqualTo(t("09:00"));
		assertThat(slots.get(0).get("endTime").asText()).isEqualTo(t("10:00"));
		assertThat(slots.get(1).get("startTime").asText()).isEqualTo(t("10:00"));
		assertThat(slots.get(2).get("startTime").asText()).isEqualTo(t("11:00"));
		assertThat(slots.get(2).get("endTime").asText()).isEqualTo(t("12:00"));
	}

	@Test
	void listExcludesSlotEndingExactlyAtFromAndSlotStartingExactlyAtTo() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("08:00"), t("10:00"));
		UUID inside1 = api().createSlot(user, t("10:00"), t("11:00"));
		UUID inside2 = api().createSlot(user, t("11:00"), t("12:00"));
		api().createSlot(user, t("12:00"), t("13:00"));

		listSlots(user, t("10:00"), t("12:00")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(inside1.toString()))
				.andExpect(jsonPath("$[1].id").value(inside2.toString()));
	}

	@Test
	void listIsSortedByStartTimeAscendingRegardlessOfCreationOrder() throws Exception {
		UUID user = api().createUser();
		UUID late = api().createSlot(user, t("13:00"), t("14:00"));
		UUID early = api().createSlot(user, t("09:00"), t("10:00"));
		UUID middle = api().createSlot(user, t("11:00"), t("12:00"));

		listSlots(user, t("00:00"), t("23:00")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(early.toString()))
				.andExpect(jsonPath("$[1].id").value(middle.toString()))
				.andExpect(jsonPath("$[2].id").value(late.toString()));
	}

	@Test
	void listExcludesOtherUsersSlots() throws Exception {
		UUID alice = api().createUser();
		UUID bob = api().createUser();
		UUID aliceSlot = api().createSlot(alice, t("09:00"), t("10:00"));
		api().createSlot(bob, t("09:00"), t("10:00"));
		api().createSlot(bob, t("11:00"), t("12:00"));

		listSlots(alice, t("00:00"), t("23:00")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(aliceSlot.toString()))
				.andExpect(jsonPath("$[0].userId").value(alice.toString()));
	}

	@Test
	void listReturnsEmptyArrayWhenNoSlotIntersects() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("09:00"), t("10:00"));

		listSlots(user, t("12:00"), t("13:00")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
		listSlots(api().createUser(), t("00:00"), t("23:00")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void listIncludesSlotCompletelyContainingTheRange() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("08:00"), t("18:00"));

		listSlots(user, t("12:00"), t("13:00")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(slot.toString()))
				.andExpect(jsonPath("$[0].startTime").value(t("08:00")))
				.andExpect(jsonPath("$[0].endTime").value(t("18:00")));
	}

	@Test
	void listReturnsStatusOfEachSlot() throws Exception {
		UUID user = api().createUser();
		api().createSlot(user, t("09:00"), t("10:00"));
		UUID busy = api().createSlot(user, t("10:00"), t("11:00"));
		api().patchStatus(user, busy, "BUSY").andExpect(status().isOk());

		listSlots(user, t("00:00"), t("23:00")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("FREE")).andExpect(jsonPath("$[1].status").value("BUSY"));
	}

	@Test
	void listWithFromEqualToToIsValidationErrorOnTo() throws Exception {
		UUID user = api().createUser();

		assertValidationError(listSlots(user, t("10:00"), t("10:00")), "to");
	}

	@Test
	void listWithFromAfterToIsValidationErrorOnTo() throws Exception {
		UUID user = api().createUser();

		assertValidationError(listSlots(user, t("11:00"), t("10:00")), "to");
	}

	@Test
	void listWithoutFromIsValidationErrorOnFrom() throws Exception {
		UUID user = api().createUser();

		assertValidationError(mockMvc.perform(get(slotsUrl(user)).param("to", t("10:00"))), "from");
	}

	@Test
	void listWithoutToIsValidationErrorOnTo() throws Exception {
		UUID user = api().createUser();

		assertValidationError(mockMvc.perform(get(slotsUrl(user)).param("from", t("10:00"))), "to");
	}

	@Test
	void listWithMalformedFromIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(listSlots(user, "yesterday", t("10:00")));
	}

	@Test
	void listWithMalformedToIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(listSlots(user, t("10:00"), "tomorrow"));
	}

	@Test
	void listWithTimestampWithoutOffsetIsMalformedRequest() throws Exception {
		UUID user = api().createUser();

		assertMalformed(listSlots(user, "2030-01-01T09:00:00", "2030-01-01T10:00:00"));
	}

	@Test
	void listAcceptsOffsetEncodedInTheQueryString() throws Exception {
		UUID user = api().createUser();
		UUID slot = api().createSlot(user, t("09:00"), t("10:00"));

		// 10:00+01:00 == 09:00Z, 11:00+01:00 == 10:00Z; MockMvc param() values are not URL-decoded again.
		listSlots(user, "2030-01-01T10:00:00+01:00", "2030-01-01T11:00:00+01:00").andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(slot.toString()));
	}

	@Test
	void listForUnknownUserIsUserNotFound() throws Exception {
		assertProblem(listSlots(UUID.randomUUID(), t("09:00"), t("10:00")), 404, "user-not-found");
	}

	@Test
	void listWithInvalidRangeForUnknownUserIsValidationErrorNotUserNotFound() throws Exception {
		assertValidationError(listSlots(UUID.randomUUID(), t("10:00"), t("09:00")), "to");
	}

	// ----------------------------------------------------------------- helpers

	/** A fixed far-future timestamp on 2030-01-01, e.g. {@code t("09:00")} is {@code 2030-01-01T09:00:00Z}. */
	private static String t(String hourMinute) {
		return "2030-01-01T" + hourMinute + ":00Z";
	}

	private static String slotsUrl(UUID userId) {
		return "/api/v1/users/" + userId + "/slots";
	}

	private static String slotUrl(UUID userId, UUID slotId) {
		return slotsUrl(userId) + "/" + slotId;
	}

	private static String body(String start, String end, String status) {
		String statusPart = status == null ? "" : ",\"status\":\"" + status + "\"";
		return "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"" + statusPart + "}";
	}

	private ResultActions getSlot(UUID userId, UUID slotId) throws Exception {
		return mockMvc.perform(get(slotUrl(userId, slotId)));
	}

	private ResultActions putSlot(UUID userId, UUID slotId, String json) throws Exception {
		return mockMvc.perform(put(slotUrl(userId, slotId)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchRaw(UUID userId, UUID slotId, String json) throws Exception {
		return mockMvc.perform(patch(slotUrl(userId, slotId) + "/availability").contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private ResultActions deleteSlot(UUID userId, UUID slotId) throws Exception {
		return mockMvc.perform(delete(slotUrl(userId, slotId)));
	}

	private ResultActions listSlots(UUID userId, String from, String to) throws Exception {
		return mockMvc.perform(get(slotsUrl(userId)).param("from", from).param("to", to));
	}

	/** Creates a FREE slot and books it through a meeting, which marks it BUSY. */
	private UUID bookedSlot(UUID userId, String start, String end) throws Exception {
		UUID slot = api().createSlot(userId, start, end);
		api().postMeeting(userId, slot, "Booking").andExpect(status().isCreated());
		return slot;
	}

	private void assertSlotUnchanged(UUID userId, UUID slotId, String start, String end, String status)
			throws Exception {
		getSlot(userId, slotId).andExpect(status().isOk()).andExpect(jsonPath("$.startTime").value(start))
				.andExpect(jsonPath("$.endTime").value(end)).andExpect(jsonPath("$.status").value(status));
	}

	private static void assertProblem(ResultActions result, int status, String problemCode) throws Exception {
		result.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM + problemCode))
				.andExpect(jsonPath("$.status").value(status));
	}

	private static void assertValidationError(ResultActions result, String field) throws Exception {
		assertProblem(result, 400, "validation-error");
		result.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").isNotEmpty());
	}

	private static void assertMalformed(ResultActions result) throws Exception {
		assertProblem(result, 400, "malformed-request");
		result.andExpect(jsonPath("$.errors").doesNotExist());
	}

}
