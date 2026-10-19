CREATE TABLE app_user
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE calendar
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE time_slot
(
    id          UUID PRIMARY KEY,
    calendar_id UUID        NOT NULL REFERENCES calendar (id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    status      VARCHAR(16) NOT NULL CHECK (status IN ('FREE', 'BUSY')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_slot_calendar_time ON time_slot (calendar_id, starts_at, ends_at);
CREATE TABLE meeting
(
    id           UUID PRIMARY KEY,
    organizer_id UUID         NOT NULL REFERENCES app_user (id),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_meeting_time ON meeting (starts_at, ends_at);
CREATE TABLE meeting_participant
(
    meeting_id UUID        NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    role       VARCHAR(16) NOT NULL CHECK (role IN ('ORGANIZER', 'PARTICIPANT')),
    PRIMARY KEY (meeting_id, user_id)
);
CREATE TABLE reservation
(
    id         UUID PRIMARY KEY,
    meeting_id UUID        NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_reservation_user_time ON reservation (user_id, starts_at, ends_at) WHERE active = TRUE;
