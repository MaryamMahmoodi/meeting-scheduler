package com.demo.meetingscheduler.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test JVM, shared by every Spring context the tests create
 * (for example a context with a spy bean). Flyway migrates it when the first context starts.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	static {
		POSTGRES.start();
	}

	// destroyMethod = "": the container outlives any single context and is removed by Ryuk at JVM exit.
	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		return POSTGRES;
	}

}
