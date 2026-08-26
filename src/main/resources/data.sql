-- =========================================================================
-- Initial Seed Data for Local Development & Testing Environments
-- =========================================================================

-- 1. Seed Accounts (Main, Savings, Secondary/Merchant, Blocked)
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
