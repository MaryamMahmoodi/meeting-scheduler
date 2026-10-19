package com.demo.meetingscheduler.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.demo.meetingscheduler.support.Api;
import com.demo.meetingscheduler.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Real races against PostgreSQL. The service pre-checks cannot see each other's uncommitted work, so
 * these exercise the per-user lock and the database constraints behind them. Requests that lose a race
 * must get a 409 problem, never a 500 (deadlock, unmapped constraint violation).
 */
class ConcurrencyTest extends IntegrationTest {

	private static final int ROUNDS = 5;
	private static final int THREADS = 6;

	@Test
	void concurrentOverlappingSlotCreatesLeaveExactlyOneSlot() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			UUID user = api().createUser();
			List<Callable<MvcResult>> requests = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				String start = "2031-03-01T10:%02d:00Z".formatted(i);
				String end = "2031-03-01T11:%02d:00Z".formatted(i);
				requests.add(() -> mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders
								.post("/api/v1/users/" + user + "/slots").contentType(MediaType.APPLICATION_JSON)
								.content(Api.slotBody(start, end)))
						.andReturn());
			}

			List<MvcResult> results = runConcurrently(requests);

			assertOneWinnerRestOverlap(results, 201, "slot-overlap");
			String body = mockMvc.perform(get("/api/v1/users/" + user + "/slots")
					.param("from", "2031-03-01T00:00:00Z").param("to", "2031-03-02T00:00:00Z"))
					.andReturn().getResponse().getContentAsString();
			assertThat(objectMapper.readTree(body)).hasSize(1);
		}
	}

	@Test
	void concurrentUpdatesIntoTheSameRangeLeaveExactlyOneWinner() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			UUID user = api().createUser();
			List<Callable<MvcResult>> requests = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				UUID slot = api().createSlot(user, "2031-04-01T%02d:00:00Z".formatted(i + 1),
						"2031-04-01T%02d:30:00Z".formatted(i + 1));
				requests.add(() -> mockMvc.perform(put("/api/v1/users/" + user + "/slots/" + slot)
						.contentType(MediaType.APPLICATION_JSON)
						.content(Api.slotBody("2031-04-01T12:00:00Z", "2031-04-01T13:00:00Z"))).andReturn());
			}

			assertOneWinnerRestOverlap(runConcurrently(requests), 200, "slot-overlap");
		}
	}

	@Test
	void concurrentRegistrationsOfTheSameEmailCreateExactlyOneUser() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String body = "{\"name\":\"Racer\",\"email\":\"race-" + UUID.randomUUID() + "@example.com\"}";
			List<Callable<MvcResult>> requests = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				requests.add(() -> mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
								.contentType(MediaType.APPLICATION_JSON).content(body))
						.andReturn());
			}

			assertOneWinnerRestOverlap(runConcurrently(requests), 201, "email-already-registered");
		}
	}

	/** Booking locks the slot row, editing locks the user row and then the slot row: no deadlock allowed. */
	@Test
	void bookingRacingAnEditOfTheSameSlotNeverFailsWith500() throws Exception {
		for (int round = 0; round < ROUNDS * 2; round++) {
			UUID user = api().createUser();
			UUID slot = api().createSlot(user, "2031-05-01T09:00:00Z", "2031-05-01T10:00:00Z");
			List<Callable<MvcResult>> requests = List.of(
					() -> api().postMeeting(user, slot, "Race").andReturn(),
					() -> mockMvc.perform(put("/api/v1/users/" + user + "/slots/" + slot)
							.contentType(MediaType.APPLICATION_JSON)
							.content(Api.slotBody("2031-05-01T09:30:00Z", "2031-05-01T10:30:00Z"))).andReturn());

			List<MvcResult> results = runConcurrently(requests);

			int booking = results.get(0).getResponse().getStatus();
			int edit = results.get(1).getResponse().getStatus();
			assertThat(booking).isEqualTo(201);
			// Either the edit won (200) and the booking then used the moved slot, or the booking won and the
			// slot is now immutable (409 slot-booked). Both orders are legal; a 500 is not.
			assertThat(edit).isIn(200, 409);
			JsonNode slotNow = objectMapper.readTree(mockMvc
					.perform(get("/api/v1/users/" + user + "/slots/" + slot)).andReturn().getResponse()
					.getContentAsString());
			assertThat(slotNow.get("status").asText()).isEqualTo("BUSY");
		}
	}

	private void assertOneWinnerRestOverlap(List<MvcResult> results, int winnerStatus, String loserProblem)
			throws Exception {
		List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).toList();
		assertThat(statuses).as("statuses %s", statuses).containsOnlyOnce(winnerStatus);
		for (MvcResult result : results) {
			if (result.getResponse().getStatus() != winnerStatus) {
				assertThat(result.getResponse().getStatus()).isEqualTo(409);
				assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("type").asText())
						.isEqualTo("urn:mini-dood:problem:" + loserProblem);
			}
		}
	}

	/** Starts all requests at the same instant. */
	private static List<MvcResult> runConcurrently(List<Callable<MvcResult>> requests) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(requests.size());
		try {
			CyclicBarrier start = new CyclicBarrier(requests.size());
			List<Future<MvcResult>> futures = new ArrayList<>();
			for (Callable<MvcResult> request : requests) {
				futures.add(executor.submit(() -> {
					start.await(10, TimeUnit.SECONDS);
					return request.call();
				}));
			}
			List<MvcResult> results = new ArrayList<>();
			for (Future<MvcResult> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

}
