const migrationUrl = new URL('../../migrations/20261106160000_transactional_admin_mutations_audit.sql', import.meta.url);
const migration = await Deno.readTextFile(migrationUrl);

Deno.test('privileged database mutation RPCs require an active owner and are service-role only', () => {
  const functions = [
    'admin_record_invited_member',
    'admin_update_member_status',
    'admin_update_device_revocation',
    'admin_set_repair_tool_status',
    'admin_save_repair_tool_draft',
    'admin_delete_draft_repair_tool',
  ];
  for (const name of functions) {
    if (!migration.includes(`FUNCTION public.${name}(`)) throw new Error(`Missing mutation RPC: ${name}`);
  }
  if (!migration.includes('private.assert_active_owner(p_actor_id)')) {
    throw new Error('Mutation RPCs must assert active Owner on the server');
  }
  if (!migration.includes('FROM PUBLIC, anon, authenticated')) {
    throw new Error('Browser execute privileges must be revoked');
  }
  if (!/GRANT EXECUTE ON FUNCTION public\.admin_update_member_status[\s\S]*?TO service_role/.test(migration)) {
    throw new Error('Service role must be the only exposed mutation caller');
  }
});

Deno.test('domain mutation and immutable audit insert are in the same SQL function bodies', () => {
  const functions = [
    'admin_record_invited_member',
    'admin_update_member_status',
    'admin_update_device_revocation',
    'admin_set_repair_tool_status',
    'admin_save_repair_tool_draft',
    'admin_delete_draft_repair_tool',
  ];
  for (let i = 0; i < functions.length; i++) {
    const marker = `CREATE OR REPLACE FUNCTION public.${functions[i]}(`;
    const start = migration.indexOf(marker);
    const next = migration.indexOf('CREATE OR REPLACE FUNCTION', start + marker.length);
    const end = next < 0 ? migration.length : next;
    const body = migration.slice(start, end);
    if (!body.includes('INSERT INTO public.admin_audit_logs')) {
      throw new Error(`Missing atomic audit insert for ${functions[i]}`);
    }
  }
});
