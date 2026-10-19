CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(254) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE time_slots
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ NOT NULL,
    status     VARCHAR(10) NOT NULL CHECK (status IN ('FREE', 'BUSY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);

CREATE INDEX idx_time_slot_user_time ON time_slots (user_id, start_time, end_time);

CREATE TABLE meetings
(
    id           UUID PRIMARY KEY,
    organizer_id UUID         NOT NULL,
    slot_id      UUID         NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(2000),
    start_time   TIMESTAMPTZ  NOT NULL,
    end_time     TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);

CREATE INDEX idx_meeting_time ON meetings (start_time, end_time);

CREATE TABLE meeting_participants
(
    meeting_id UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL,
    PRIMARY KEY (meeting_id, user_id)
);