-- SpaceBooking schema (PostgreSQL 16). Flyway V1__init.sql must equal this file.
-- All TIMESTAMP columns hold UTC. Business timezone: Asia/Ho_Chi_Minh (see domain.md).

-- Needed by the bookings exclusion constraint (equality on uuid inside a GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN'))
);

CREATE TABLE rooms (
    id                UUID         PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    location          VARCHAR(255) NOT NULL,
    capacity          INT          NOT NULL,
    equipments        VARCHAR(500) NOT NULL DEFAULT '',
    requires_approval BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_rooms_name UNIQUE (name),
    CONSTRAINT ck_rooms_capacity CHECK (capacity BETWEEN 1 AND 1000),
    -- comma-separated lowercase tokens, no spaces, or empty
    CONSTRAINT ck_rooms_equipments CHECK (equipments ~ '^([a-z0-9-]+(,[a-z0-9-]+)*)?$')
);

CREATE TABLE bookings (
    id               UUID         PRIMARY KEY,
    room_id          UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    title            VARCHAR(255) NOT NULL,
    start_time       TIMESTAMP    NOT NULL,
    end_time         TIMESTAMP    NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    rejection_reason VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT fk_bookings_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_bookings_time CHECK (end_time > start_time),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_bookings_rejection CHECK (rejection_reason IS NULL OR status = 'CANCELLED'),
    -- Backstop for the optimistic-lock protocol: no two blocking bookings of one room may overlap ([start, end) semantics).
    CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN'))
);

-- Overlap check and daily schedule (spec 1.3).
CREATE INDEX ix_bookings_room_status_time ON bookings (room_id, status, start_time, end_time);
-- my-bookings paging.
CREATE INDEX ix_bookings_user_start ON bookings (user_id, start_time DESC);
-- Scheduler scans and the pending queue.
CREATE INDEX ix_bookings_status_start ON bookings (status, start_time);

CREATE TABLE audit_logs (
    id           UUID         PRIMARY KEY,
    booking_id   UUID         NOT NULL,
    action       VARCHAR(30)  NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    details      TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_audit_logs_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('CREATED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED',
                                                      'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED', 'NO_SHOW'))
);

CREATE INDEX ix_audit_logs_booking_created ON audit_logs (booking_id, created_at);
CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);
