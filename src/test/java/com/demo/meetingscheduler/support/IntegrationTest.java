package com.demo.meetingscheduler.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for tests that run the whole application against a real PostgreSQL (Testcontainers).
 * All subclasses share one Spring context and one database, so tests must create their own users
 * (see {@link Api#createUser()}) and never assume empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	protected Api api() {
		return new Api(mockMvc, objectMapper);
	}

}
