CREATE TABLE outbox_projection_clock (
    id SMALLINT NOT NULL,
    current_version BIGINT NOT NULL,
    CONSTRAINT pk_outbox_projection_clock PRIMARY KEY (id),
    CONSTRAINT ck_outbox_projection_clock_singleton CHECK (id = 1)
);

INSERT INTO outbox_projection_clock (id, current_version)
VALUES (1, 0);

ALTER TABLE outbox_events
    ADD COLUMN projection_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ALTER COLUMN projection_version DROP DEFAULT;
