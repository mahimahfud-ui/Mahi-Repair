const migrationUrl = new URL('../../migrations/20261106120000_private_realtime_catalog_broadcast.sql', import.meta.url);
const migration = await Deno.readTextFile(migrationUrl);

Deno.test('private Realtime policy is active-member and own-device scoped', () => {
  const required = [
    'FOR SELECT',
    'TO authenticated',
    "p.role = 'member'",
    "p.status = 'active'",
    "extension = 'broadcast'",
    'private IS TRUE',
    'd.revoked_at IS NULL',
    "'mahi-repair:member:' || d.user_id::text || ':device:' || d.id::text",
  ];
  for (const item of required) {
    if (!migration.includes(item)) throw new Error(`Realtime policy is missing ${item}`);
  }
  if (/CREATE POLICY[\s\S]*?TO anon/i.test(migration)) {
    throw new Error('Anonymous Realtime access must not be granted');
  }
  if (!migration.includes('CREATE TRIGGER repair_tools_private_catalog_broadcast') ||
      !migration.includes('CREATE TRIGGER repair_tool_access_private_catalog_broadcast') ||
      !migration.includes('CREATE TRIGGER profiles_private_access_broadcast') ||
      !migration.includes('CREATE TRIGGER devices_private_access_broadcast')) {
    throw new Error('A catalog/member/device invalidation trigger is missing');
  }
});

Deno.test('Realtime broadcasts contain only an opaque event ID and are private', () => {
  if (!migration.includes("jsonb_build_object('event_id', gen_random_uuid()::text)")) {
    throw new Error('Broadcast payload is not restricted to opaque event ID');
  }
  if (!/jsonb_build_object\('event_id',[\s\S]*?TRUE\s*\)/.test(migration)) {
    throw new Error('Realtime send must set private = true');
  }
  if (/jsonb_build_object\([^)]*(?:recipe|signature|email|token)/i.test(migration)) {
    throw new Error('Sensitive data must never be present in a Realtime event payload');
  }
  for (const event of ['catalog_changed', 'member_access_changed', 'device_revoked']) {
    if (!migration.includes(event)) throw new Error(`Missing required invalidation event: ${event}`);
  }
});

Deno.test('Realtime contract documents cached existing connections and offline reconnect sync', () => {
  if (!/caches the authorization result for the lifetime of that connection/i.test(migration)) {
    throw new Error('Connection authorization caching is not documented');
  }
  if (!/Offline devices receive no event while/i.test(migration)) {
    throw new Error('Offline-device reconnect behavior is not documented');
  }
});
