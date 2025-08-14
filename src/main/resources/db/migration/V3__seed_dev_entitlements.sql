-- =========================================
-- V3: User, Subscription
-- =========================================

-- DEV SEED: 2 user
INSERT INTO users(id, email, password_hash, display_name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'dev@example.com', '$2a$10$tPIS8pyGKjEv3eMADHLVBeQYkOctBUN7/iHpZ867h47vcswgBG7Lu', 'dev');

INSERT INTO user_devices(id, user_id, platform, app_version) VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'android', '0.0.1');

-- DEV SEED: 1 user premium_all active

INSERT INTO users(id, email, password_hash)
SELECT '11111111-1111-1111-1111-111111111111', 'dev@example.com', 'dev'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id='11111111-1111-1111-1111-111111111111');

INSERT INTO user_devices(id, user_id, platform, app_version)
SELECT '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'android', '0.0.1'
WHERE NOT EXISTS (SELECT 1 FROM user_devices WHERE id='22222222-2222-2222-2222-222222222222');

-- grant entitlement active (lifetime)
INSERT INTO user_entitlements(id, user_id, code, source, starts_at, ends_at)
SELECT
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'premium_all',
    'admin_grant',
    now(),
    NULL
WHERE NOT EXISTS (SELECT 1 FROM user_entitlements WHERE id='33333333-3333-3333-3333-333333333333');
