package com.demo.meetingscheduler.adapter.outbound.persistence;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.demo.meetingscheduler.support.IntegrationTest;

/**
 * Atomicity of booking: the slot change to BUSY and the meeting insert commit or roll back together.
 * Lives in its own class because the spy bean needs its own Spring context (the PostgreSQL container is shared).
 */
class MeetingRollbackTest extends IntegrationTest {

	private static final String DAY_START = "2030-01-01T00:00:00Z";
	private static final String DAY_END = "2030-01-02T00:00:00Z";

	@MockitoSpyBean
	private MeetingJpaRepository meetingRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void slotChangeIsRolledBackWhenTheMeetingInsertFailsBeforeReachingTheDatabase() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		doThrow(new RuntimeException("simulated insert failure")).when(meetingRepository).saveAndFlush(any());

		book(organizer, slot).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:internal-error"))
				.andExpect(jsonPath("$.status").value(500));

		verify(meetingRepository, Mockito.times(1)).saveAndFlush(any());
		Mockito.reset(meetingRepository);
		assertSlotStatus(organizer, slot, "FREE");
		assertNoMeetings(organizer);

		book(organizer, slot).andExpect(status().isCreated()).andExpect(jsonPath("$.slotId").value(slot.toString()));
		assertSlotStatus(organizer, slot, "BUSY");
		listMeetings(organizer).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void slotChangeAndMeetingInsertAlreadyFlushedToTheDatabaseAreRolledBackWhenTheRequestFailsAfterwards()
			throws Exception {
		UUID organizer = api().createUser();
		UUID participant = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		// The meeting INSERT and the slot UPDATE are flushed in the open transaction, then the call fails:
		// everything the transaction already sent to the database must still be rolled back.
		doAnswer(invocation -> {
			entityManager.persist(invocation.<MeetingJpaEntity>getArgument(0));
			entityManager.flush();
			throw new RuntimeException("simulated failure after flush");
		}).when(meetingRepository).saveAndFlush(any());

		api().postMeeting(organizer, slot, "Rolled back", participant).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("urn:mini-dood:problem:internal-error"));

		Mockito.reset(meetingRepository);
		assertSlotStatus(organizer, slot, "FREE");
		assertNoMeetings(organizer);
		assertNoMeetings(participant);

		api().postMeeting(organizer, slot, "Second try", participant).andExpect(status().isCreated());
		assertSlotStatus(organizer, slot, "BUSY");
		listMeetings(organizer).andExpect(jsonPath("$", hasSize(1)));
		listMeetings(participant).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void retryAfterResettingTheSpySucceeds() throws Exception {
		UUID organizer = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		doThrow(new RuntimeException("simulated insert failure")).when(meetingRepository).saveAndFlush(any());

		book(organizer, slot).andExpect(status().isInternalServerError());
		assertSlotStatus(organizer, slot, "FREE");

		Mockito.reset(meetingRepository);

		book(organizer, slot).andExpect(status().isCreated());
		assertSlotStatus(organizer, slot, "BUSY");
		api().getAvailability(organizer, DAY_START, DAY_END).andExpect(jsonPath("$.slots[0].status").value("BUSY"));
	}

	@Test
	void failedBookingDoesNotAffectOtherUsersSlotsOrMeetings() throws Exception {
		UUID organizer = api().createUser();
		UUID other = api().createUser();
		UUID slot = api().createSlot(organizer, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		UUID otherSlot = api().createSlot(other, "2030-01-01T09:00:00Z", "2030-01-01T10:00:00Z");
		doThrow(new RuntimeException("simulated insert failure")).when(meetingRepository).saveAndFlush(any());

		book(organizer, slot).andExpect(status().isInternalServerError());
		Mockito.reset(meetingRepository);
		book(other, otherSlot).andExpect(status().isCreated());

		assertSlotStatus(organizer, slot, "FREE");
		assertSlotStatus(other, otherSlot, "BUSY");
		assertNoMeetings(organizer);
		listMeetings(other).andExpect(jsonPath("$", hasSize(1)));
	}

	private ResultActions book(UUID organizer, UUID slot) throws Exception {
		return api().postJson("/api/v1/users/" + organizer + "/meetings",
				"{\"slotId\":\"" + slot + "\",\"title\":\"Atomic\"}");
	}

	private ResultActions listMeetings(UUID userId) throws Exception {
		return mockMvc.perform(get("/api/v1/users/" + userId + "/meetings").param("from", DAY_START)
				.param("to", DAY_END)).andExpect(status().isOk());
	}

	private void assertNoMeetings(UUID userId) throws Exception {
		listMeetings(userId).andExpect(jsonPath("$", hasSize(0)));
	}

	private void assertSlotStatus(UUID userId, UUID slotId, String expected) throws Exception {
		mockMvc.perform(get("/api/v1/users/" + userId + "/slots/" + slotId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(expected));
	}

}
