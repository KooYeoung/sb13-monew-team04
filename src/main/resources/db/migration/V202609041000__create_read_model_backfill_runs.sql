CREATE TABLE read_model_backfill_runs (
    run_id UUID NOT NULL,
    stage VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_processed_id UUID NULL,
    pending_last_id UUID NULL,
    processed_count BIGINT NOT NULL DEFAULT 0,
    claim_id UUID NULL,
    claimed_at TIMESTAMP NULL,
    claim_until TIMESTAMP NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    verification_report TEXT NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_read_model_backfill_runs PRIMARY KEY (run_id)
);
