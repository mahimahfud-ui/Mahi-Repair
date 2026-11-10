import { ConsoleShell } from "@/components/console-shell";
import { requireOwner } from "@/lib/auth/require-owner";
import { formatDate } from "@/lib/utils";

export const dynamic = "force-dynamic";
type VersionRow = { id: string; toolId: string; version: number; signatureAlgorithm: string; publishedAt: string; changelog?: string };

export default async function PublishingPage() {
  const { api } = await requireOwner();
  let data: VersionRow[] = [];
  let error: string | null = null;
  try { data = await api.listToolVersions<VersionRow[]>({ limit: 50 }); }
  catch (cause) { error = cause instanceof Error ? cause.message : "Published releases are unavailable."; }

  return <ConsoleShell active="/publishing" title="Publishing Center" subtitle="Signed, immutable recipe releases">
    <div className="panel mb-4 p-5"><h2 className="text-sm font-semibold">Release gate</h2><p className="muted mt-2 text-xs">A release is created only after server-side recipe validation, canonicalization and RSA-SHA256 signing. A dry run never counts as device testing.</p></div>
    <div className="panel overflow-hidden">
      <div className="border-b border-[#252830] p-4 text-sm">Published releases</div>
      {error ? <p className="p-4 text-sm text-red-300">{error}</p> : data.length ? <div className="divide-y divide-[#252830]">{data.map((version) => <div key={version.id} className="flex items-center justify-between gap-3 p-4"><div><div className="text-sm font-medium">Tool {version.toolId.slice(0, 8)} · v{version.version}</div><div className="muted mt-1 text-[11px]">{version.signatureAlgorithm} · {version.changelog || "No changelog"}</div></div><div className="muted text-[11px]">{formatDate(version.publishedAt)}</div></div>)}</div> : <p className="p-8 text-center text-sm muted">No recipes published yet.</p>}
    </div>
  </ConsoleShell>;
}
