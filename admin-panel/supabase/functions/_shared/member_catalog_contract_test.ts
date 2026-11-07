const migrationUrl = new URL('../../migrations/20261106170000_atomic_member_catalog_and_device_registration.sql', import.meta.url);
const migration = await Deno.readTextFile(migrationUrl);

Deno.test('device registration locks and validates active membership and device ownership', () => {
  for (const token of [
    'register_device_for_member',
    'v_profile.role <> \'member\'',
    "v_profile.status <> 'active'",
    'FOR SHARE',
    'FOR UPDATE',
    'v_device.user_id <> p_user_id',
    'v_device.revoked_at IS NOT NULL',
    'GRANT EXECUTE ON FUNCTION public.register_device_for_member',
  ]) {
    if (!migration.includes(token)) throw new Error(`Device registration SQL is missing ${token}`);
  }
});

Deno.test('catalog fetch returns only current published assigned compatible signed recipes and records sync', () => {
  for (const token of [
    'get_member_repair_catalog',
    "t.status = 'PUBLISHED'",
    "t.audience = 'all_active_members'",
    'a.member_id = p_user_id',
    'FOR SHARE OF t',
    'FOR KEY SHARE',
    'version = v_tool.version',
    'private.app_version_at_least',
    'v_device.android_api < v_min_api',
    'INSERT INTO public.device_tool_syncs',
    "'signed_payload', v_version.canonical_payload",
    'GRANT EXECUTE ON FUNCTION public.get_member_repair_catalog',
  ]) {
    if (!migration.includes(token)) throw new Error(`Member catalog SQL is missing ${token}`);
  }
});
