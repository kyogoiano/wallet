CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sequence BIGINT DEFAULT 0
);


-- Ledger: source of truth
CREATE TABLE IF NOT EXISTS ledger (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    reference_id UUID,
    operation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- tamper-proof fields
    sequence BIGINT NOT NULL,
    hash VARCHAR(128) NOT NULL,
    previous_hash VARCHAR(128), -- correctness depends on ordering

    CONSTRAINT ledger_wallet_fk
        FOREIGN KEY (wallet_id) REFERENCES accounts(id),

    CONSTRAINT ledger_type_chk
        CHECK (type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ledger_amount_chk
        CHECK (amount > 0),

    CONSTRAINT ledger_operation_unique
        UNIQUE (operation_id, wallet_id),

    CONSTRAINT ledger_sequence_unique
        UNIQUE (wallet_id, sequence)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_ledger_wallet_time
    ON ledger (wallet_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ledger_reference
    ON ledger (reference_id);

CREATE INDEX IF NOT EXISTS idx_ledger_prev_hash
    ON ledger (previous_hash);

CREATE INDEX IF NOT EXISTS idx_ledger_wallet_sequence_desc
    ON ledger (wallet_id, sequence DESC);

-- Outbox for event publishing
CREATE TABLE IF NOT EXISTS outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL, -- wallet operation
    aggregate_id UUID NOT NULL, -- operation id
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ NULL,
    next_retry_at TIMESTAMPTZ NULL,
    CONSTRAINT outbox_status_chk
        CHECK (status IN ('PENDING', 'FAILED', 'PROCESSING', 'PROCESSED', 'DEAD')),
    CONSTRAINT outbox_event_type_chk -- might be removed for flexibility
        CHECK (event_type IN ('TRANSFER_COMPLETED', 'DEPOSIT_COMPLETED', 'WITHDRAW_COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed
    ON outbox (processed_at)
    WHERE processed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_ready
    ON outbox (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE IF NOT EXISTS wallet_operations (
    operation_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    --status: PROCESSING | COMPLETED
);