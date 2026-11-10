import { ConsoleShell } from "@/components/console-shell";
import { requireOwner } from "@/lib/auth/require-owner";
import { formatDate } from "@/lib/utils";
import { UserPlus, Smartphone } from "lucide-react";
import { inviteMember, setMemberStatus, setDeviceRevoked } from "@/app/actions";

export const dynamic = "force-dynamic";

type Member = { id: string; email: string; displayName?: string; role: "member"; status: string; createdAt?: string };
type Device = { id: string; userId: string; deviceLabel?: string; appVersion?: string; androidVersion?: string; androidApi?: number; lastSeenAt?: string; revokedAt?: string | null };

export default async function MembersPage() {
  const { api } = await requireOwner();
  let members: Member[] = [];
  let devices: Device[] = [];
  let error: string | null = null;
  let deviceError: string | null = null;
  const results = await Promise.allSettled([api.listMembers<Member[]>(), api.listDevices<Device[]>()]);
  if (results[0].status === "fulfilled") members = results[0].value;
  else error = results[0].reason instanceof Error ? results[0].reason.message : "Member list unavailable.";
  if (results[1].status === "fulfilled") devices = results[1].value;
  else deviceError = results[1].reason instanceof Error ? results[1].reason.message : "Device list unavailable.";
  const memberById = new Map(members.map((member) => [member.id, member]));

  return <ConsoleShell active="/members" title="Members & Devices" subtitle="Invite trusted users and control account/device access">
    <div className="panel mb-4 p-5">
      <div className="mb-4 flex items-center gap-3"><UserPlus className="text-red-400" /><div><h2 className="text-sm font-semibold">Invite a member</h2><p className="mt-1 text-xs muted">Invitation creates a Member account only. Owner role is never assigned through this form.</p></div></div>
      <form action={inviteMember} className="flex flex-col gap-2 sm:flex-row"><input className="field" type="email" name="email" required maxLength={254} placeholder="family@example.com" /><button className="btn btn-primary shrink-0">Send invitation</button></form>
    </div>

    <div className="panel overflow-hidden">
      <div className="border-b border-[#252830] p-4 text-sm font-medium">Member accounts · {members.length}</div>
      {error ? <p className="p-4 text-sm text-red-300">{error}</p> : <div className="overflow-x-auto"><table className="w-full text-sm"><thead className="text-[10px] muted"><tr><th className="p-4 text-left">MEMBER</th><th className="p-4 text-left">STATUS</th><th className="p-4 text-left">CREATED</th><th className="p-4 text-left">ACCESS CONTROL</th></tr></thead><tbody className="divide-y divide-[#252830]">{members.map((member) => <tr key={member.id}><td className="p-4"><div className="text-xs">{member.displayName || "Member"}</div><div className="mt-1 text-[11px] muted">{member.email || member.id}</div></td><td className="p-4 text-xs">{member.status}</td><td className="p-4 text-xs muted">{member.createdAt ? formatDate(member.createdAt) : "—"}</td><td className="p-4"><form action={setMemberStatus} className="flex gap-2"><input type="hidden" name="user_id" value={member.id} /><select name="status" defaultValue={member.status} className="field py-2 text-xs"><option value="active">Active</option><option value="suspended">Suspended</option><option value="revoked">Revoked</option></select><button className="btn btn-secondary py-2 text-xs">Save</button></form></td></tr>)}</tbody></table></div>}
    </div>

    <div className="panel mt-4 overflow-hidden">
      <div className="flex items-center gap-2 border-b border-[#252830] p-4 text-sm font-medium"><Smartphone size={15} className="text-red-400" /> Registered devices · {devices.length}</div>
      {deviceError ? <p className="p-4 text-sm text-red-300">{deviceError}</p> : devices.length ? <div className="overflow-x-auto"><table className="w-full text-xs"><thead className="text-[10px] muted"><tr><th className="p-4 text-left">DEVICE</th><th className="p-4 text-left">MEMBER</th><th className="p-4 text-left">APP / ANDROID</th><th className="p-4 text-left">LAST SEEN</th><th className="p-4 text-left">STATE / CONTROL</th></tr></thead><tbody className="divide-y divide-[#252830]">{devices.map((device) => { const member = memberById.get(device.userId); return <tr key={device.id}><td className="p-4"><div>{device.deviceLabel || device.id.slice(0, 8)}</div><div className="mt-1 font-mono muted">{device.id.slice(0, 12)}…</div></td><td className="p-4">{member?.email || device.userId.slice(0, 8)}</td><td className="p-4">{device.appVersion || "—"} / Android {device.androidVersion || "—"} (API {device.androidApi ?? "—"})</td><td className="p-4 muted">{device.lastSeenAt ? formatDate(device.lastSeenAt) : "—"}</td><td className="p-4"><div className="flex items-center gap-2"><span>{device.revokedAt ? "REVOKED" : "ACTIVE"}</span><form action={setDeviceRevoked}><input type="hidden" name="device_id" value={device.id} /><input type="hidden" name="revoked" value={device.revokedAt ? "false" : "true"} /><button className="btn btn-secondary px-2 py-1 text-[10px]">{device.revokedAt ? "Restore" : "Revoke"}</button></form></div></td></tr>; })}</tbody></table></div> : <p className="p-6 text-center text-xs muted">No Android devices have registered.</p>}
    </div>
  </ConsoleShell>;
}
