import { ConsoleShell } from '@/components/console-shell';
import { requireOwner } from '@/lib/auth/require-owner';
import { formatDate } from '@/lib/utils';
import { UserPlus, Smartphone, Mail } from 'lucide-react';
import { inviteMember, setMemberStatus, setDeviceRevoked } from '@/app/actions';

export const dynamic = 'force-dynamic';

export default async function MembersPage() {
  const { supabase } = await requireOwner();
  const [
    { data: members, error },
    { data: devices, error: deviceError },
    { data: invitations, error: invitationError },
    { data: deviceToolSyncs, error: deviceToolSyncError },
  ] = await Promise.all([
    supabase.from('profiles')
      .select('id,email,display_name,role,status,created_at')
      .eq('role', 'member')
      .order('created_at', { ascending: false }),
    supabase.from('devices')
      .select('id,user_id,device_label,app_version,android_version,android_api,last_seen_at,revoked_at')
      .order('last_seen_at', { ascending: false }),
    supabase.from('admin_audit_logs')
      .select('id,target_id,created_at')
      .eq('action', 'member.invited')
      .order('created_at', { ascending: false })
      .limit(50),
    supabase.from('device_tool_syncs')
      .select('device_id,tool_id,version,synced_at')
      .order('synced_at', { ascending: false })
      .limit(500),
  ]);

  const syncedToolIds = [...new Set((deviceToolSyncs ?? []).map((sync) => sync.tool_id))];
  const { data: syncedTools, error: syncedToolError } = syncedToolIds.length
    ? await supabase.from('repair_tools').select('id,name,slug,status').in('id', syncedToolIds)
    : { data: [], error: null };

  const memberById = new Map((members ?? []).map((member) => [member.id, member]));
  const toolById = new Map((syncedTools ?? []).map((tool) => [tool.id, tool]));
  const syncsByDevice = new Map<string, Array<{ device_id: string; tool_id: string; version: number; synced_at: string }>>();
  for (const sync of deviceToolSyncs ?? []) {
    const rows = syncsByDevice.get(sync.device_id) ?? [];
    rows.push(sync);
    syncsByDevice.set(sync.device_id, rows);
  }

  return (
    <ConsoleShell active="/members" title="Members & Devices" subtitle="Invite trusted users and control account/device access">
      <div className="panel mb-4 p-5">
        <div className="mb-4 flex items-center gap-3">
          <UserPlus className="text-red-400" />
          <div>
            <h2 className="text-sm font-semibold">Invite a member</h2>
            <p className="mt-1 text-xs muted">The authenticated Edge Function creates a member invitation. Owner role is never assigned through this form.</p>
          </div>
        </div>
        <form action={inviteMember} className="flex flex-col gap-2 sm:flex-row">
          <input className="field" type="email" name="email" required maxLength={254} placeholder="family@example.com" />
          <button className="btn btn-primary shrink-0">Send invitation</button>
        </form>
      </div>

      <div className="panel overflow-hidden">
        <div className="border-b border-[#252830] p-4 text-sm font-medium">Member account access · {members?.length ?? 0}</div>
        {error ? (
          <p className="p-4 text-sm text-red-300">Unable to load member accounts.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-[10px] muted">
                <tr>
                  <th className="p-4 text-left">MEMBER</th>
                  <th className="p-4 text-left">ACCOUNT ACCESS</th>
                  <th className="p-4 text-left">PROFILE CREATED</th>
                  <th className="p-4 text-left">ACCESS CONTROL</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#252830]">
                {(members ?? []).map((member) => (
                  <tr key={member.id}>
                    <td className="p-4">
                      <div className="text-xs">{member.display_name || 'Member'}</div>
                      <div className="mt-1 text-[11px] muted">{member.email || member.id}</div>
                    </td>
                    <td className="p-4 text-xs">{member.status}</td>
                    <td className="p-4 text-xs muted">{formatDate(member.created_at)}</td>
                    <td className="p-4">
                      <form action={setMemberStatus} className="flex gap-2">
                        <input type="hidden" name="user_id" value={member.id} />
                        <select name="status" defaultValue={member.status} className="field py-2 text-xs">
                          <option value="active">Active</option>
                          <option value="suspended">Suspended</option>
                          <option value="revoked">Revoked</option>
                        </select>
                        <button className="btn btn-secondary py-2 text-xs">Save</button>
                      </form>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel mt-4 overflow-hidden">
        <div className="flex items-center gap-2 border-b border-[#252830] p-4 text-sm font-medium">
          <Mail size={15} className="text-red-400" /> Invitation request history
        </div>
        <div className="border-b border-[#252830] bg-[#0b0d11] p-4 text-xs leading-5 muted">
          These rows are audit records created after Supabase Auth accepted an invite request. The current backend does not expose inbox delivery, invitation acceptance time, expiration, resend, or per-invitation revocation. The profile access status above is not proof that an email was delivered or accepted.
        </div>
        {invitationError ? (
          <p className="p-4 text-sm text-red-300">Invitation audit history is unavailable.</p>
        ) : invitations?.length ? (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead className="muted">
                <tr>
                  <th className="p-4 text-left">INVITATION REQUESTED</th>
                  <th className="p-4 text-left">MEMBER ACCOUNT</th>
                  <th className="p-4 text-left">CURRENT ACCOUNT ACCESS</th>
                  <th className="p-4 text-left">USER ID</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#252830]">
                {invitations.map((invite) => {
                  const member = invite.target_id ? memberById.get(invite.target_id) : undefined;
                  return (
                    <tr key={invite.id}>
                      <td className="whitespace-nowrap p-4 muted">{formatDate(invite.created_at)}</td>
                      <td className="p-4">{member?.email || 'Profile not available'}</td>
                      <td className="p-4">{member?.status || 'Unknown'}</td>
                      <td className="p-4 font-mono muted">{invite.target_id || '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="p-6 text-center text-xs muted">No invitation requests have been recorded.</p>
        )}
      </div>

      <div className="panel mt-4 overflow-hidden">
        <div className="flex items-center gap-2 border-b border-[#252830] p-4 text-sm font-medium">
          <Smartphone size={15} className="text-red-400" /> Registered devices · {devices?.length ?? 0}
        </div>
        {deviceError ? (
          <p className="p-4 text-sm text-red-300">Unable to load registered devices.</p>
        ) : devices?.length ? (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead className="text-[10px] muted">
                <tr>
                  <th className="p-4 text-left">DEVICE LABEL / ID</th>
                  <th className="p-4 text-left">MEMBER</th>
                  <th className="p-4 text-left">APP / ANDROID</th>
                  <th className="p-4 text-left">LAST CHECK-IN</th>
                  <th className="p-4 text-left">RECORDED CATALOGUE SYNC</th>
                  <th className="p-4 text-left">ACCESS / CONTROL</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#252830]">
                {devices.map((device) => {
                  const member = memberById.get(device.user_id);
                  return (
                    <tr key={device.id}>
                      <td className="p-4">
                        <div>{device.device_label || 'Unlabelled device'}</div>
                        <div className="mt-1 font-mono muted">{device.id}</div>
                      </td>
                      <td className="p-4">{member?.email || device.user_id}</td>
                      <td className="p-4">{device.app_version} / Android {device.android_version} (API {device.android_api ?? '—'})</td>
                      <td className="whitespace-nowrap p-4 muted">{formatDate(device.last_seen_at)}</td>
                      <td className="p-4">
                        {deviceToolSyncError || syncedToolError ? (
                          <span className="text-red-300">Catalogue sync history unavailable</span>
                        ) : (syncsByDevice.get(device.id) ?? []).length ? (
                          <div className="min-w-[220px] space-y-2">
                            {(syncsByDevice.get(device.id) ?? []).map((sync) => {
                              const tool = toolById.get(sync.tool_id);
                              return (
                                <div key={sync.tool_id}>
                                  <div>{tool?.name || sync.tool_id}</div>
                                  <div className="text-[10px] muted">
                                    v{sync.version} · {tool?.status || 'Unknown status'} · first recorded {formatDate(sync.synced_at)}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <span className="muted">No recorded tool sync</span>
                        )}
                      </td>
                      <td className="p-4">
                        <div className="flex items-center gap-2">
                          <span>{device.revoked_at ? 'REVOKED' : 'NOT REVOKED'}</span>
                          <form action={setDeviceRevoked}>
                            <input type="hidden" name="device_id" value={device.id} />
                            <input type="hidden" name="revoked" value={device.revoked_at ? 'false' : 'true'} />
                            <button className="btn btn-secondary px-2 py-1 text-[10px]">{device.revoked_at ? 'Restore' : 'Revoke'}</button>
                          </form>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="p-6 text-center text-xs muted">No Android devices have registered.</p>
        )}
        <p className="border-t border-[#252830] p-4 text-[11px] muted">
          The Android registration contract supplies device label, app version, Android version/API, last check-in and revocation. The catalogue column shows the version most recently upserted by the real device sync endpoint. The synced_at value is the row's first-recorded timestamp (the current Edge Function does not refresh it on upsert); this is historical sync telemetry, not proof of current access or hardware compatibility. Manufacturer/model, pairing state, battery, storage and detailed hardware capability telemetry are not uploaded and are intentionally omitted.
        </p>
      </div>
    </ConsoleShell>
  );
}
