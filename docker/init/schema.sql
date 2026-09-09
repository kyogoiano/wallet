CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE accounts (
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
    partition_key UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ NULL,
    next_retry_at TIMESTAMPTZ NULL,
    CONSTRAINT outbox_status_chk
        CHECK (status IN ('PENDING', 'FAILED', 'PROCESSING', 'PROCESSED', 'DEAD')),
    CONSTRAINT outbox_event_type_chk -- might be removed for flexibility
        CHECK (event_type IN ('TRANSFER_COMPLETED', 'DEPOSIT_COMPLETED', 'WITHDRAW_COMPLETED', 'FRAUD', 'RISK_PROPAGATION_DETECTED'))
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
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    error_message TEXT NULL,
    failure_type VARCHAR(32) NULL,
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
  failure_type TEXT NOT NULL,
  event_type VARCHAR(50) NOT NULL
      CHECK (failure_type IN ('TRANSIENT', 'BUSINESS', 'POISON')),
  CONSTRAINT dlq_status_chk
      CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'COMPLETED', 'EXHAUSTED', 'DISCARDED')),
  CONSTRAINT dlq_operations_pkey PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS idx_dlq_retry
    ON dlq_operations (next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_dlq_exhausted
    ON dlq_operations (status, created_at)
    WHERE status = 'EXHAUSTED';

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
-- =========================================================================
-- Initial Seed Data for Local Development & Testing Environments
-- =========================================================================

-- 1. Seed Accounts (Main, Savings, Merchant, Blocked)
INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at)
VALUES 
    ('0a35fb14-75ee-4125-943b-500893c30d33', 10000.00, 0, 'a1111111-1111-1111-1111-111111111111', 'ACTIVE', 0, NOW()),
    ('1b46fc25-86ff-5236-a54c-611904d41e44', 0.00,     0, 'a1111111-1111-1111-1111-111111111111', 'ACTIVE', 0, NOW()),
    ('2c57ad36-97aa-6347-b65d-722015e52f55', 5000.00,  0, 'b2222222-2222-2222-2222-222222222222', 'ACTIVE', 0, NOW()),
    ('3d68be47-08bb-7458-c76e-833126f63a66', 1000.00,  0, 'c3333333-3333-3333-3333-333333333333', 'BLOCKED', 0, NOW())
ON CONFLICT (id) DO NOTHING;

UPDATE accounts 
SET blocked_at = NOW(), blocked_reason = 'Seeded blocked account for security testing'
WHERE id = '3d68be47-08bb-7458-c76e-833126f63a66' AND blocked_at IS NULL;

-- 2. Seed Initial Savings Plans
INSERT INTO savings_plans (id, source_wallet_id, target_wallet_id, minimum_retained_balance, status, created_at, updated_at)
VALUES 
    ('d1111111-1111-1111-1111-111111111111', '0a35fb14-75ee-4125-943b-500893c30d33', '1b46fc25-86ff-5236-a54c-611904d41e44', 100.00, 'ACTIVE', NOW(), NOW()),
    ('d2222222-2222-2222-2222-222222222222', '2c57ad36-97aa-6347-b65d-722015e52f55', '1b46fc25-86ff-5236-a54c-611904d41e44', 500.00, 'ACTIVE', NOW(), NOW()),
    ('d3333333-3333-3333-3333-333333333333', '0a35fb14-75ee-4125-943b-500893c30d33', '2c57ad36-97aa-6347-b65d-722015e52f55', 1000.00, 'PAUSED', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. Seed Initial Savings Rules (Round-Up, Percentage, Threshold)
INSERT INTO savings_rules (id, plan_id, rule_type, step_amount, percentage_rate, ceiling_threshold, is_active, created_at)
VALUES 
    -- Rules for Plan 1 (Main -> Savings)
    ('a1111111-2222-3333-4444-555555555551', 'd1111111-1111-1111-1111-111111111111', 'ROUND_UP', 5.00, NULL, NULL, TRUE, NOW()),
    ('a1111111-2222-3333-4444-555555555552', 'd1111111-1111-1111-1111-111111111111', 'PERCENTAGE', NULL, 10.0000, NULL, TRUE, NOW()),
    ('a1111111-2222-3333-4444-555555555553', 'd1111111-1111-1111-1111-111111111111', 'THRESHOLD', NULL, NULL, 5000.00, TRUE, NOW()),
    -- Rules for Plan 2 (Secondary -> Savings)
    ('b1111111-2222-3333-4444-555555555551', 'd2222222-2222-2222-2222-222222222222', 'ROUND_UP', 10.00, NULL, NULL, TRUE, NOW()),
    ('b1111111-2222-3333-4444-555555555552', 'd2222222-2222-2222-2222-222222222222', 'PERCENTAGE', NULL, 5.0000, NULL, TRUE, NOW()),
    -- Rules for Plan 3 (Main -> Secondary, Paused)
    ('c1111111-2222-3333-4444-555555555551', 'd3333333-3333-3333-3333-333333333333', 'THRESHOLD', NULL, NULL, 8000.00, TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

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

-- =========================================================================
-- Hybrid Fraud Intelligence & Relational Graph Tables (SPEC-000.5 / PLAN-000.5)
-- =========================================================================

CREATE TABLE IF NOT EXISTS fraud_entities (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    direct_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    graph_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    behavioral_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    propagated_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    final_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    propagation_model_version VARCHAR(32) DEFAULT 'v1',
    propagation_evaluated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_fraud_entities_type ON fraud_entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_fraud_entities_propagated ON fraud_entities(propagated_risk DESC) WHERE propagated_risk > 0.0;

CREATE TABLE IF NOT EXISTS fraud_relationships (
    source_id UUID NOT NULL,
    target_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tx_count BIGINT NOT NULL DEFAULT 1,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    metadata JSONB,
    PRIMARY KEY (source_id, target_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_fraud_rel_source ON fraud_relationships (source_id, relationship_type);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_target ON fraud_relationships (target_id, relationship_type);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_last_seen ON fraud_relationships (last_seen_at);

CREATE TABLE IF NOT EXISTS fraud_relationship_events (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    target_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    operation_id UUID,
    amount NUMERIC(19, 4),
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_src_time ON fraud_relationship_events (source_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_tgt_time ON fraud_relationship_events (target_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_type_time ON fraud_relationship_events (relationship_type, occurred_at);

-- =========================================================================
-- Risk Propagation & Durable Job Queue Tables (SPEC-000.6 / PLAN-000.6)
-- =========================================================================

CREATE TABLE IF NOT EXISTS fraud_propagation_jobs (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    as_of TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    worker_token UUID,
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    model_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial unique index guaranteeing active job uniqueness (History 23)
CREATE UNIQUE INDEX IF NOT EXISTS idx_fraud_prop_active_unique 
ON fraud_propagation_jobs (entity_id, model_version) 
WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT');

-- Index for high-speed worker polling with SKIP LOCKED
CREATE INDEX IF NOT EXISTS idx_fraud_prop_jobs_claim 
ON fraud_propagation_jobs (available_at, created_at) 
WHERE status IN ('PENDING', 'RETRY_WAIT');

CREATE INDEX IF NOT EXISTS idx_fraud_prop_jobs_entity 
ON fraud_propagation_jobs (entity_id, status);

-- =========================================================================
-- Behavioral Embeddings, Archetype Centroids & Async Queue (SPEC-000.7 / PLAN-000.7)
-- =========================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS fraud_entity_features (
    entity_id UUID PRIMARY KEY REFERENCES fraud_entities(id) ON DELETE CASCADE,
    feature_version INT NOT NULL DEFAULT 1,
    behavioral_vector vector(16) NOT NULL,
    feature_magnitude DOUBLE PRECISION NOT NULL,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    transaction_volume NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_archetype_centroids (
    archetype_id VARCHAR(64) PRIMARY KEY,
    description TEXT NOT NULL,
    centroid_vector vector(16) NOT NULL,
    risk_weight NUMERIC(3, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_embedding_jobs (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL REFERENCES fraud_entities(id) ON DELETE CASCADE,
    model_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    as_of TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    worker_token UUID,
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Active job deduplication index (Histories 23 & 28)
CREATE UNIQUE INDEX IF NOT EXISTS idx_fraud_emb_active_unique 
ON fraud_embedding_jobs (entity_id, model_version) 
WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT');

-- High-speed worker polling with SKIP LOCKED
CREATE INDEX IF NOT EXISTS idx_fraud_emb_jobs_claim 
ON fraud_embedding_jobs (available_at, created_at) 
WHERE status IN ('PENDING', 'RETRY_WAIT');

CREATE INDEX IF NOT EXISTS idx_fraud_emb_jobs_entity 
ON fraud_embedding_jobs (entity_id, status);

-- Seed Calibrated Fraud Archetypes (L2-normalized unit vectors)
INSERT INTO fraud_archetype_centroids (archetype_id, description, centroid_vector, risk_weight)
VALUES
    ('MONEY_MULE_RAPID_DRAIN', 'Rapid pass-through funds drain with high velocity and nocturnal activity', 
     '[0.1849, 0.2311, 0.1387, 0.3698, 0.3236, 0.1849, 0.1849, 0.4160, 0.3698, 0.2774, 0.0462, 0.0924, 0.1387, 0.2311, 0.0924, 0.3236]', 0.95),
    ('SMURFING', 'High frequency micro-transactions with dispersed counterparties and low amount variance',
     '[0.3996, 0.0666, 0.0222, 0.3108, 0.1332, 0.3996, 0.3774, 0.1332, 0.3774, 0.3774, 0.0444, 0.0444, 0.0888, 0.1776, 0.0444, 0.2664]', 0.85),
    ('ACCOUNT_TAKEOVER', 'Sudden new hardware/device switch with failed authentications and out-of-pattern spikes',
     '[0.1826, 0.2922, 0.2922, 0.2191, 0.1826, 0.1096, 0.2191, 0.3104, 0.2556, 0.0730, 0.1461, 0.3287, 0.3470, 0.3287, 0.1826, 0.3104]', 0.90)
ON CONFLICT (archetype_id) DO NOTHING;

-- 4. Seed Fraud Entities & Intelligence Graph Data (SPEC-000.5)
INSERT INTO fraud_entities (id, entity_type, direct_risk, graph_risk, behavioral_risk, propagated_risk, final_risk, created_at, updated_at)
VALUES
    ('a1111111-1111-1111-1111-111111111111', 'USER',   0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW()),
    ('b2222222-2222-2222-2222-222222222222', 'USER',   0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW()),
    ('c3333333-3333-3333-3333-333333333333', 'USER',   0.9, 0.0, 0.0, 0.0, 0.9, NOW(), NOW()),
    ('0a35fb14-75ee-4125-943b-500893c30d33', 'WALLET', 0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW()),
    ('2c57ad36-97aa-6347-b65d-722015e52f55', 'WALLET', 0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW()),
    ('3d68be47-08bb-7458-c76e-833126f63a66', 'WALLET', 0.9, 0.0, 0.0, 0.0, 0.9, NOW(), NOW()),
    ('e5555555-5555-5555-5555-555555555555', 'DEVICE', 0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW()),
    ('f6666666-6666-6666-6666-666666666666', 'IP',     0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Seed Ownership & Topology Edges
INSERT INTO fraud_relationships (source_id, target_id, relationship_type, first_seen_at, last_seen_at, tx_count, total_amount)
VALUES
    ('a1111111-1111-1111-1111-111111111111', '0a35fb14-75ee-4125-943b-500893c30d33', 'OWNS', NOW(), NOW(), 1, 0.0000),
    ('b2222222-2222-2222-2222-222222222222', '2c57ad36-97aa-6347-b65d-722015e52f55', 'OWNS', NOW(), NOW(), 1, 0.0000),
    ('c3333333-3333-3333-3333-333333333333', '3d68be47-08bb-7458-c76e-833126f63a66', 'OWNS', NOW(), NOW(), 1, 0.0000),
    ('a1111111-1111-1111-1111-111111111111', 'e5555555-5555-5555-5555-555555555555', 'USES', NOW(), NOW(), 1, 0.0000),
    ('b2222222-2222-2222-2222-222222222222', 'e5555555-5555-5555-5555-555555555555', 'USES', NOW(), NOW(), 1, 0.0000),
    ('0a35fb14-75ee-4125-943b-500893c30d33', '2c57ad36-97aa-6347-b65d-722015e52f55', 'TRANSFERRED_TO', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', 1, 500.0000),
    ('2c57ad36-97aa-6347-b65d-722015e52f55', '3d68be47-08bb-7458-c76e-833126f63a66', 'TRANSFERRED_TO', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes', 1, 450.0000)
ON CONFLICT (source_id, target_id, relationship_type) DO NOTHING;

-- Seed Temporal Event Stream
INSERT INTO fraud_relationship_events (id, source_id, target_id, relationship_type, occurred_at, operation_id, amount)
VALUES
    ('fa111111-1111-1111-1111-111111111111', '0a35fb14-75ee-4125-943b-500893c30d33', '2c57ad36-97aa-6347-b65d-722015e52f55', 'TRANSFERRED_TO', NOW() - INTERVAL '1 hour', gen_random_uuid(), 500.0000),
    ('fb222222-2222-2222-2222-222222222222', '2c57ad36-97aa-6347-b65d-722015e52f55', '3d68be47-08bb-7458-c76e-833126f63a66', 'TRANSFERRED_TO', NOW() - INTERVAL '30 minutes', gen_random_uuid(), 450.0000)
ON CONFLICT (id) DO NOTHING;

-- Hand-Rolled Durable Job Queue with Partial Unique Index Coalescing (REQ-FUSION-004, REQ-FUSION-009, REQ-FUSION-013)
CREATE TABLE IF NOT EXISTS fraud_fusion_jobs (
    job_id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    model_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    as_of TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempt_count INT NOT NULL DEFAULT 0,
    worker_token UUID,
    lease_expires_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    idempotency_key VARCHAR(128),
    payload JSONB,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial Unique Index: Guarantees at most ONE pending job per entity, while preserving full execution history
CREATE UNIQUE INDEX IF NOT EXISTS uq_fusion_job_pending_entity
    ON fraud_fusion_jobs (entity_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_fusion_jobs_poll 
    ON fraud_fusion_jobs (status, as_of, lease_expires_at);

-- Checkpoints for Human-in-the-Loop Compliance Review (Under REVIEW / RESTRICT)
CREATE TABLE IF NOT EXISTS fraud_investigation_checkpoints (
    checkpoint_id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ANALYST',
    state_payload JSONB NOT NULL,
    final_risk NUMERIC(4, 3) NOT NULL,
    risk_classification VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fraud_checkpoints_entity 
    ON fraud_investigation_checkpoints (entity_id, status);

-- Audit Log for Human-in-the-Loop Analyst Decisions (REQ-FUSION-008)
CREATE TABLE IF NOT EXISTS fraud_analyst_reviews (
    review_id UUID PRIMARY KEY,
    checkpoint_id UUID NOT NULL REFERENCES fraud_investigation_checkpoints(checkpoint_id),
    entity_id UUID NOT NULL,
    analyst_id VARCHAR(64) NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    notes TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fraud_reviews_checkpoint 
    ON fraud_analyst_reviews (checkpoint_id);

--TODO: on high concurrency envs include pgbouncer proxy connection pooler on stack with transaction mode enabled this will improve the reuse of connections