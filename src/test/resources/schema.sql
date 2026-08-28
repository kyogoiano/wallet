CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    blocked_at TIMESTAMPTZ NULL,
    blocked_reason TEXT NULL,
    last_sequence BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'SUSPENDED', 'FROZEN'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status) WHERE status != 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_accounts_user_status ON accounts(user_id, status);

-- Ledger: source of truth
CREATE TABLE IF NOT EXISTS ledger (
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
    partition_key UUID NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed
    ON outbox (processed_at)
    WHERE processed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_ready
    ON outbox (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE IF NOT EXISTS wallet_operations (
    operation_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    CONSTRAINT wallet_operations_status_chk
    CHECK (status IN ('FAILED', 'COMPLETED', 'PROCESSING'))
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
    failure_type TEXT NOT NULL,
    event_type VARCHAR(50) NOT NULL
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

-- =========================================================================
-- Savings Capability Module Tables (PLAN-001)
-- =========================================================================

CREATE TABLE IF NOT EXISTS savings_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_wallet_id UUID NOT NULL REFERENCES accounts(id),
    target_wallet_id UUID NOT NULL REFERENCES accounts(id),
    minimum_retained_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_diff_wallets CHECK (source_wallet_id != target_wallet_id),
    CONSTRAINT chk_min_balance CHECK (minimum_retained_balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_savings_plans_source ON savings_plans(source_wallet_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_savings_plans_source_all ON savings_plans(source_wallet_id);
CREATE INDEX IF NOT EXISTS idx_savings_plans_target ON savings_plans(target_wallet_id);

CREATE TABLE IF NOT EXISTS savings_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES savings_plans(id) ON DELETE CASCADE,
    rule_type VARCHAR(32) NOT NULL, -- ROUND_UP, PERCENTAGE, THRESHOLD
    step_amount NUMERIC(19, 2),
    percentage_rate NUMERIC(7, 4),
    ceiling_threshold NUMERIC(19, 2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rule_config CHECK (
        (rule_type = 'ROUND_UP' AND step_amount > 0) OR
        (rule_type = 'PERCENTAGE' AND percentage_rate > 0 AND percentage_rate <= 100) OR
        (rule_type = 'THRESHOLD' AND ceiling_threshold > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_savings_rules_plan ON savings_rules(plan_id) WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS savings_execution_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL UNIQUE, -- Layer 1 Deduplication Key
    plan_id UUID NOT NULL REFERENCES savings_plans(id),
    rule_id UUID NOT NULL REFERENCES savings_rules(id),
    source_operation_id UUID NOT NULL,
    trigger_event_type VARCHAR(64) NOT NULL,
    calculated_amount NUMERIC(19, 2) NOT NULL,
    swept_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_savings_hist_source_op ON savings_execution_history(source_operation_id);
CREATE INDEX IF NOT EXISTS idx_savings_hist_plan ON savings_execution_history(plan_id, created_at DESC);

-- =========================================================================
-- Financial Goals Capability Module Tables (SPEC-002 / PLAN-002)
-- =========================================================================

CREATE TABLE IF NOT EXISTS goals (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL REFERENCES accounts(id),
    target_wallet_id UUID NULL REFERENCES accounts(id),
    name VARCHAR(128) NOT NULL,
    target_amount NUMERIC(19, 2) NOT NULL CHECK (target_amount > 0),
    target_date DATE NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_goal_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_goal_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ACHIEVED', 'CANCELLED'))
    );

CREATE INDEX IF NOT EXISTS idx_goals_user ON goals(user_id);
CREATE INDEX IF NOT EXISTS idx_goals_wallet_status ON goals(wallet_id, status);

CREATE TABLE IF NOT EXISTS cashflow_profiles (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    monthly_income NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (monthly_income >= 0),
    monthly_committed_expenses NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (monthly_committed_expenses >= 0),
    minimum_safety_buffer NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (minimum_safety_buffer >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_cashflow_wallet ON cashflow_profiles(wallet_id);