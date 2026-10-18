# Meeting Scheduler API

A mini Doodle-style REST API using Java 25, Spring Boot, PostgreSQL, Flyway, Docker Compose, and Hexagonal Architecture.

## Features
- Create and retrieve users; each user gets a domain calendar.
- Create/update/delete slots and mark them free or busy.
- Book meetings with an organizer and participants.
- Check free slots and active reservations before booking.
- Persist meeting participants and per-user reservations transactionally.
- Cancel meetings and release reservations.
- Query free/busy availability and a calendar view by time range.
- Actuator health and metrics endpoints.

## Architecture
- `domain`: time-range invariant and availability status.
- `application/port/in`: use-case interface.
- `application/port/out`: persistence port.
- `application/service`: use-case orchestration.
- `adapter/in/web`: REST controllers and error mapping.
- `adapter/out/persistence`: PostgreSQL JDBC adapter.
- Flyway owns the schema.

## Run
Requirements: Docker with Compose. For local Gradle commands, Java 25 and Gradle are required.

```bash
docker compose up --build -d
docker compose ps
```
API: http://localhost:8080/api  
Health: http://localhost:8080/actuator/health  
Stop: `docker compose down`

For local app execution, start only the database and run the app with Java 25:
```bash
docker compose up -d postgres
gradle bootRun
```

## API examples

Create a user:
```bash
curl -X POST http://localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"Alice","email":"alice@example.com"}'
```

Create a slot (replace `USER_ID`):
```bash
curl -X POST http://localhost:8080/api/users/USER_ID/slots -H 'Content-Type: application/json' -d '{"startsAt":"2026-10-20T10:00:00+02:00","endsAt":"2026-10-20T11:00:00+02:00"}'
```

Book a meeting after creating matching slots for organizer and participant:
```bash
curl -X POST http://localhost:8080/api/meetings -H 'Content-Type: application/json' -d '{"organizerId":"ALICE_USER_ID","title":"Project sync","description":"Planning","startsAt":"2026-10-20T10:00:00+02:00","endsAt":"2026-10-20T10:30:00+02:00","participantIds":["BOB_USER_ID"]}'
```

Query availability:
```bash
curl 'http://localhost:8080/api/users/USER_ID/availability?from=2026-10-20T00:00:00%2B02:00&to=2026-10-21T00:00:00%2B02:00'
```

Cancel:
```bash
curl -X DELETE http://localhost:8080/api/meetings/MEETING_ID
```

## Business rules and assumptions
- Date-times use ISO-8601 with an offset.
- Intervals are half-open: `[startsAt, endsAt)`, so adjacent meetings do not overlap.
- A booking requires every participant to have a FREE slot covering the full meeting interval.
- Active reservations prevent overlapping bookings.
- Booking runs in a transaction and locks user rows in deterministic order.
- Slots cannot be changed/deleted while busy or overlapping an active reservation.
- Authentication/authorization is out of scope for this challenge baseline.

## Known limitations / future improvements
This is a coding-challenge baseline and should be built and tested locally before submission. Recommended next improvements: enforce non-overlapping free slots, stronger database-level overlap constraints, authorization, idempotency, richer error payloads, Testcontainers integration tests, and OpenAPI/Swagger docs.

## Test
```bash
gradle test
gradle clean build
```
