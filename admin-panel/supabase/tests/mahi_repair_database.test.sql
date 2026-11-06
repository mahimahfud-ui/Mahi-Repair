-- Mahi Repair local Supabase database acceptance tests.
-- This file runs only in the disposable Supabase CLI stack in GitHub Actions.
-- All fixtures are synthetic and the transaction is rolled back at the end.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;

SELECT plan(17);

-- Synthetic Owner and Member accounts. The auth trigger creates their profiles.
INSERT INTO auth.users (
  id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
VALUES
  (
    '10000000-0000-4000-8000-000000000001',
    'authenticated', 'authenticated', 'staging-owner@example.invalid', '',
    now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
    now(), now()
  ),
  (
    '10000000-0000-4000-8000-000000000002',
    'authenticated', 'authenticated', 'staging-member@example.invalid', '',
    now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
    now(), now()
  );

UPDATE public.profiles
SET role = 'owner'
WHERE id = '10000000-0000-4000-8000-000000000001';

-- Two devices prove member isolation from the Owner's device.
INSERT INTO public.devices (
  id, user_id, device_label, app_version, android_version, android_api
)
VALUES
  (
    '20000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001',
    'Synthetic Owner Device', '1.0.0', '16', 35
  ),
  (
    '20000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000002',
    'Synthetic Member Device', '1.0.0', '16', 35
  );

-- One published tool and one draft. Only the published tool is member-visible.
INSERT INTO public.repair_tools (
  id, name, slug, description, category, icon, status, audience,
  version, draft_recipe, created_by
)
VALUES
  (
    '30000000-0000-4000-8000-000000000001',
    'Staging Connectivity Check', 'staging-connectivity-check',
    'Synthetic database test recipe', 'network', 'wifi',
    'PUBLISHED', 'all_active_members', 1,
    '{"schemaVersion":"1","steps":[{"id":"network_probe"}]}'::jsonb,
    '10000000-0000-4000-8000-000000000001'
  ),
  (
    '30000000-0000-4000-8000-000000000002',
    'Staging Draft Only', 'staging-draft-only',
    'Synthetic unpublished recipe', 'network', 'wrench',
    'DRAFT', 'all_active_members', 0,
    '{"schemaVersion":"1","steps":[]}'::jsonb,
    '10000000-0000-4000-8000-000000000001'
  );

INSERT INTO public.repair_tool_versions (
  tool_id, version, payload, canonical_payload, signature,
  signature_algorithm, changelog, published_by
)
VALUES (
  '30000000-0000-4000-8000-000000000001',
  1,
  '{
    "schemaVersion":"1",
    "toolId":"staging-connectivity-check",
    "version":1,
    "minAndroidApi":23,
    "minAppVersion":"1.0.0",
    "steps":[{"id":"network_probe"}]
  }'::jsonb,
  '{"schemaVersion":"1","toolId":"staging-connectivity-check","version":1,"steps":[{"id":"network_probe"}]}',
  'synthetic-test-signature-not-for-app-use',
  'RSA-SHA256',
  'Synthetic pgTAP fixture',
  '10000000-0000-4000-8000-000000000001'
);

INSERT INTO public.admin_audit_logs (
  actor_id, action, target_type, target_id, details
)
VALUES (
  '10000000-0000-4000-8000-000000000001',
  'staging.fixture.created', 'repair_tool',
  '30000000-0000-4000-8000-000000000001',
  '{"synthetic":true}'::jsonb
);

-- 1–7: Member RLS and direct-write boundary.
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '10000000-0000-4000-8000-000000000002';

SELECT is(
  (SELECT count(*)::integer FROM public.profiles),
  1,
  'member can read only their own profile'
);

SELECT is(
  (SELECT count(*)::integer FROM public.profiles
   WHERE id = '10000000-0000-4000-8000-000000000001'),
  0,
  'member cannot read the Owner profile'
);

SELECT is(
  (SELECT count(*)::integer FROM public.devices),
  1,
  'member can read only their own registered device'
);

SELECT is(
  (SELECT count(*)::integer FROM public.repair_tools),
  1,
  'member sees the published tool but not the draft'
);

SELECT is(
  (SELECT count(*)::integer FROM public.repair_tool_versions),
  1,
  'member sees only the signed version for an accessible published tool'
);

SELECT is(
  (SELECT count(*)::integer FROM public.admin_audit_logs),
  0,
  'member cannot read Owner audit records'
);

SELECT throws_ok(
  $$
    INSERT INTO public.repair_runs (
      id, user_id, device_id, tool_id, tool_version,
      started_at, completed_at, result, step_results, app_version
    )
    VALUES (
      '40000000-0000-4000-8000-000000000099',
      '10000000-0000-4000-8000-000000000002',
      '20000000-0000-4000-8000-000000000002',
      '30000000-0000-4000-8000-000000000001',
      1, now(), now(), 'SUCCESS',
      '[{"step_id":"network_probe","status":"SUCCESS"}]'::jsonb,
      '1.0.0'
    )
  $$,
  '42501',
  NULL,
  'authenticated member cannot insert a repair report directly'
);

-- 8–9: Owner visibility.
RESET ROLE;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '10000000-0000-4000-8000-000000000001';

SELECT is(
  (SELECT count(*)::integer FROM public.profiles),
  2,
  'active Owner can read both Owner and Member profiles'
);

SELECT is(
  (SELECT count(*)::integer FROM public.repair_tools),
  2,
  'active Owner can read published and draft tools'
);

-- 10: Every core application table has RLS enabled after all migrations.
RESET ROLE;

SELECT is(
  (
    SELECT count(*)::integer
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND c.relname = ANY (ARRAY[
        'profiles',
        'devices',
        'repair_tools',
        'repair_tool_versions',
        'repair_tool_access',
        'device_tool_syncs',
        'repair_runs',
        'admin_audit_logs',
        'recipe_test_history'
      ])
      AND c.relrowsecurity
  ),
  9,
  'RLS is enabled on every core application table'
);

-- 11–13: Only the trusted server role may execute the idempotent report RPC.
SELECT ok(
  NOT has_function_privilege(
    'anon',
    'public.submit_repair_run_idempotent(uuid,uuid,uuid,uuid,integer,timestamptz,timestamptz,public.repair_result,jsonb,text,text,text)',
    'EXECUTE'
  ),
  'anon cannot execute the report acceptance RPC'
);

SELECT ok(
  NOT has_function_privilege(
    'authenticated',
    'public.submit_repair_run_idempotent(uuid,uuid,uuid,uuid,integer,timestamptz,timestamptz,public.repair_result,jsonb,text,text,text)',
    'EXECUTE'
  ),
  'authenticated clients cannot bypass the Edge Function report boundary'
);

SELECT ok(
  has_function_privilege(
    'service_role',
    'public.submit_repair_run_idempotent(uuid,uuid,uuid,uuid,integer,timestamptz,timestamptz,public.repair_result,jsonb,text,text,text)',
    'EXECUTE'
  ),
  'service_role can execute the report acceptance RPC'
);

-- Capture the RPC results in transaction-local settings while using service_role.
-- No fixture or report persists because the entire test file is rolled back.
SET LOCAL ROLE service_role;

SELECT set_config(
  'mahirepair_test.first',
  public.submit_repair_run_idempotent(
    '40000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002',
    '20000000-0000-4000-8000-000000000002',
    '30000000-0000-4000-8000-000000000001',
    1,
    '2026-11-07T00:00:00Z',
    '2026-11-07T00:01:00Z',
    'SUCCESS',
    '[{"step_id":"network_probe","status":"SUCCESS"}]'::jsonb,
    NULL,
    '1.0.0',
    repeat('a', 64)
  )::text,
  true
);

SELECT set_config(
  'mahirepair_test.retry',
  public.submit_repair_run_idempotent(
    '40000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002',
    '20000000-0000-4000-8000-000000000002',
    '30000000-0000-4000-8000-000000000001',
    1,
    '2026-11-07T00:00:00Z',
    '2026-11-07T00:01:00Z',
    'SUCCESS',
    '[{"step_id":"network_probe","status":"SUCCESS"}]'::jsonb,
    NULL,
    '1.0.0',
    repeat('a', 64)
  )::text,
  true
);

SELECT set_config(
  'mahirepair_test.conflict',
  public.submit_repair_run_idempotent(
    '40000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002',
    '20000000-0000-4000-8000-000000000002',
    '30000000-0000-4000-8000-000000000001',
    1,
    '2026-11-07T00:00:00Z',
    '2026-11-07T00:01:00Z',
    'SUCCESS',
    '[{"step_id":"network_probe","status":"SUCCESS"}]'::jsonb,
    NULL,
    '1.0.0',
    repeat('b', 64)
  )::text,
  true
);

RESET ROLE;

-- 14–17: First acceptance, exact retry, changed-payload conflict, one stored row.
SELECT ok(
  (current_setting('mahirepair_test.first')::jsonb ->> 'accepted') = 'true'
  AND (current_setting('mahirepair_test.first')::jsonb ->> 'duplicate') = 'false',
  'first report submission is accepted once'
);

SELECT ok(
  (current_setting('mahirepair_test.retry')::jsonb ->> 'accepted') = 'true'
  AND (current_setting('mahirepair_test.retry')::jsonb ->> 'duplicate') = 'true',
  'exact retry returns the original accepted receipt'
);

SELECT ok(
  (current_setting('mahirepair_test.conflict')::jsonb ->> 'conflict') = 'true',
  'reusing a run ID with a different fingerprint is rejected as a conflict'
);

SELECT is(
  (
    SELECT count(*)::integer
    FROM public.repair_runs
    WHERE id = '40000000-0000-4000-8000-000000000001'
  ),
  1,
  'first submission and retry produce exactly one stored report'
);

SELECT * FROM finish();

ROLLBACK;
