const idempotencyUrl = new URL('../../migrations/20261106153000_backend_report_idempotency_and_atomic_catalog.sql', import.meta.url);
const integrityUrl = new URL('../../migrations/20261106170000_atomic_member_catalog_and_device_registration.sql', import.meta.url);
const idempotency = await Deno.readTextFile(idempotencyUrl);
const integrity = await Deno.readTextFile(integrityUrl);

Deno.test('report primary key and content fingerprint implement UUID idempotency', () => {
  for (const token of [
    'ADD COLUMN request_fingerprint text',
    'p_run_id uuid',
    'WHERE id = p_run_id',
    'ON CONFLICT (id) DO NOTHING',
    "p_request_fingerprint !~ '^[0-9a-f]{64}$'",
    'v_existing.user_id = p_user_id',
    'v_existing.device_id = p_device_id',
    'STALE_RECIPE_VERSION',
    'INVALID_STEP_IDS',
  ]) {
    if (!idempotency.includes(token)) throw new Error(`Idempotency migration missing ${token}`);
  }
});

Deno.test('upgraded devices can upload immutable reports created by an older compatible app version', () => {
  const required = 'private.app_version_at_least(v_device.app_version, p_app_version)';
  if (!idempotency.includes(required)) {
    throw new Error('A queued report must retain its original app version while the registered app may be newer.');
  }
});

Deno.test('reports and device syncs are relationally bound to their device owner and recipe version', () => {
  for (const token of [
    'repair_runs_user_device_fk',
    'FOREIGN KEY (user_id, device_id)',
    'repair_runs_tool_version_fk',
    'FOREIGN KEY (tool_id, tool_version)',
    'device_tool_syncs_user_device_fk',
    'device_tool_syncs_tool_version_fk',
    'REFERENCES public.devices(user_id, id)',
    'REFERENCES public.repair_tool_versions(tool_id, version)',
  ]) {
    if (!integrity.includes(token)) throw new Error(`Relational integrity migration missing ${token}`);
  }
});
