import { ConsoleShell } from "@/components/console-shell";
import { requireOwner } from "@/lib/auth/require-owner";
import { formatDate } from "@/lib/utils";
import { Activity, Boxes, CheckCircle2, Users, ArrowUpRight } from "lucide-react";
import Link from "next/link";

export const dynamic = "force-dynamic";

type OverviewAnalytics = { totalRuns: number; success: number; partial: number; failed: number; cancelled: number };
type ToolRow = { id: string; status: string };
type MemberRow = { id: string; status: string; role: string };
type AuditRow = { id: string; action: string; createdAt: string; details?: Record<string, unknown> };

export default async function OverviewPage() {
  const { api } = await requireOwner();
  let data: { tools: ToolRow[]; members: MemberRow[]; analytics: OverviewAnalytics; recent: AuditRow[] } | null = null;
  let loadError: string | null = null;
  try {
    const [tools, members, analytics, recent] = await Promise.all([
      api.listTools<ToolRow[]>(),
      api.listMembers<MemberRow[]>(),
      api.getAnalytics<OverviewAnalytics>(),
      api.getAuditLogs<AuditRow[]>({ limit: 6 }),
    ]);
    data = { tools, members, analytics, recent };
  } catch (error) {
    loadError = error instanceof Error ? error.message : "Dashboard data is unavailable.";
  }

  const analytics = data?.analytics;
  const stats = [
    ["Repair tools", data?.tools.length ?? 0, Boxes, "All lifecycle states"],
    ["Active members", data?.members.filter((member) => member.role === "member" && member.status === "active").length ?? 0, Users, "Authorized accounts"],
    ["Executions", analytics?.totalRuns ?? 0, Activity, "Recorded repair runs"],
    ["Published tools", data?.tools.filter((tool) => tool.status === "PUBLISHED").length ?? 0, CheckCircle2, "Available for delivery"],
  ] as const;
  const success = analytics?.success ?? 0;
  const failed = analytics?.failed ?? 0;
  const partial = analytics?.partial ?? 0;
  const total = analytics?.totalRuns ?? 0;

  return <ConsoleShell active="/" title="Overview" subtitle="Your private repair ecosystem at a glance">
    {loadError && <div className="panel mb-4 p-4 text-sm text-red-300">Dashboard data is unavailable: {loadError}</div>}
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{stats.map(([label, value, Icon, description]) => <div className="panel p-5" key={label}><div className="flex items-center justify-between"><span className="text-xs muted">{label}</span><Icon size={17} className="text-red-400" /></div><div className="stat-value mt-4">{value.toLocaleString()}</div><div className="mt-2 text-[11px] muted">{description}</div></div>)}</div>
    <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1.4fr_1fr]">
      <section className="panel p-5"><div className="flex items-center justify-between"><div><h2 className="text-sm font-semibold">Execution health</h2><p className="mt-1 text-xs muted">Aggregate status from accepted run records</p></div><Link href="/logs" className="flex items-center gap-1 text-xs text-red-300">View logs <ArrowUpRight size={13} /></Link></div><div className="mt-6 grid grid-cols-3 gap-3"><div><div className="text-2xl font-bold">{success}</div><div className="mt-1 text-xs text-emerald-300">Successful</div></div><div><div className="text-2xl font-bold">{failed}</div><div className="mt-1 text-xs text-red-300">Failed</div></div><div><div className="text-2xl font-bold">{partial}</div><div className="mt-1 text-xs text-amber-300">Partial</div></div></div><div className="mt-6 flex h-2 overflow-hidden rounded-full bg-[#252830]"><div className="bg-emerald-500" style={{ width: `${total ? success / total * 100 : 0}%` }} /><div className="bg-red-500" style={{ width: `${total ? failed / total * 100 : 0}%` }} /><div className="bg-amber-400" style={{ width: `${total ? partial / total * 100 : 0}%` }} /></div><p className="mt-3 text-[11px] muted">A submitted result is telemetry, not independent proof of a successful physical repair.</p></section>
      <section className="panel p-5"><h2 className="text-sm font-semibold">Quick actions</h2><div className="mt-4 grid grid-cols-1 gap-2"><Link href="/tools/new" className="btn btn-primary justify-between">Create repair tool <ArrowUpRight size={15} /></Link><Link href="/test-lab" className="btn btn-secondary justify-between">Validate a recipe <ArrowUpRight size={15} /></Link><Link href="/members" className="btn btn-secondary justify-between">Invite a member <ArrowUpRight size={15} /></Link></div></section>
    </div>
    <section className="panel mt-5 p-5"><div className="mb-4 flex items-center justify-between"><div><h2 className="text-sm font-semibold">Recent administrative activity</h2><p className="mt-1 text-xs muted">Append-only audit trail</p></div><Link href="/security" className="text-xs text-red-300">Security center</Link></div>{data?.recent.length ? <div className="divide-y divide-[#252830]">{data.recent.map((row) => <div key={row.id} className="flex items-center justify-between gap-4 py-3"><div><div className="text-sm">{row.action}</div><div className="mt-1 text-[11px] muted">{row.details ? JSON.stringify(row.details) : "—"}</div></div><time className="whitespace-nowrap text-[11px] muted">{formatDate(row.createdAt)}</time></div>)}</div> : <div className="py-7 text-center text-sm muted">No administrative activity has been recorded yet.</div>}</section>
  </ConsoleShell>;
}
