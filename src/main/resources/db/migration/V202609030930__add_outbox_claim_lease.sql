ALTER TABLE outbox_events
    ADD COLUMN claim_id UUID NULL,
    ADD COLUMN claimed_at TIMESTAMP NULL,
    ADD COLUMN claim_until TIMESTAMP NULL;
