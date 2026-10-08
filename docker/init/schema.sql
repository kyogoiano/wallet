CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE accounts ADD last_sequence BIGINT DEFAULT 0;

-- Ledger: source of truth
CREATE TABLE ledger (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    reference_id UUID,
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL,
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
        UNIQUE (wallet_id, sequence),

    CONSTRAINT ledger_integrity_chk
        CHECK (
            (sequence = 1 AND previous_hash IS NULL) OR
            (sequence > 1 AND previous_hash IS NOT NULL)
            )
);

-- Indexes for performance
CREATE INDEX idx_ledger_wallet_time
    ON ledger (wallet_id, created_at);

CREATE INDEX idx_ledger_reference
    ON ledger (reference_id);

CREATE INDEX idx_ledger_prev_hash
    ON ledger (previous_hash);

CREATE INDEX idx_ledger_wallet_sequence_desc
    ON ledger (wallet_id, sequence DESC);

-- Outbox for event publishing
CREATE TABLE outbox (
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
        CHECK (event_type IN ('TRANSFER_COMPLETED', 'DEPOSIT_COMPLETED', 'WITHDRAW_COMPLETED', 'FRAUD'))
);

CREATE INDEX idx_outbox_unprocessed
    ON outbox (processed_at)
    WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_ready
    ON outbox (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE wallet_operations (
    operation_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    CONSTRAINT wallet_operations_status_chk
        CHECK (status IN ('FAILED', 'COMPLETED', 'PROCESSING'))
    -- TODO: include payload for debugging
    -- For high-write event workloads, increase max_wal_size and wal_buffers to reduce checkpoint frequency and improve throughput.
);

CREATE TABLE IF NOT EXISTS dlq_operations (
  id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  operation_id UUID NOT NULL,
  user_id UUID,
  subject VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  error TEXT,
  payload JSONB NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  processed_at TIMESTAMPTZ,
  failure_type TEXT NOT NULL
      CHECK (failure_type IN ('TRANSIENT', 'BUSINESS', 'POISON')),
  CONSTRAINT dlq_status_chk
      CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'COMPLETED')),
  CONSTRAINT dlq_operations_pkey PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS idx_dlq_retry
    ON dlq_operations (next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_dlq_processing
    ON dlq_operations (status, created_at)
    WHERE status = 'PROCESSING';


CREATE INDEX IF NOT EXISTS idx_dlq_pending
    ON dlq_operations (id, status)
    WHERE status IN ('PENDING');

CREATE INDEX IF NOT EXISTS idx_dlq_operation
    ON dlq_operations (operation_id);

CREATE INDEX IF NOT EXISTS idx_dlq_pending_retry
    ON dlq_operations (status, next_retry_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_dlq_failed
    ON dlq_operations (failure_type, created_at)
    WHERE status = 'FAILED';

CREATE TABLE IF NOT EXISTS dlq_operations_default
    PARTITION OF dlq_operations DEFAULT;

--TODO: on high concurrency envs include pgbouncer proxy connection pooler on stack with transaction mode enabled this will improve the reuse of connections