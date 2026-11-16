-- Use for Transactional Outbox pattern,
-- ensuring that creating an order and registering its OrderSubmitted event happen in the same PostgreSQL transaction.
-- aggregate_type = ORDER, initially
-- aggregate_id = ID of the corresponding order
-- event_type = ORDER_SUBMITTED
-- claim_token = Identifies the worker that claimed the event
-- locked_until = Allows recovery if a publisher crashes

CREATE TABLE outbox_events(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),

    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    event_type      VARCHAR(100) NOT NULL,

    payload         JSONB        NOT NULL,

    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,

    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token     UUID,
    locked_until    TIMESTAMPTZ,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    last_error      TEXT,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')),

    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),

    CONSTRAINT chk_outbox_claim
        CHECK ((status = 'IN_PROGRESS') = (claim_token IS NOT NULL AND locked_until IS NOT NULL)),

    CONSTRAINT chk_outbox_published
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_outbox_pending
    ON outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_in_progress
    ON outbox_events (locked_until)
    WHERE status = 'IN_PROGRESS';
