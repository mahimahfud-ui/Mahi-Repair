import { ConsoleShell } from "@/components/console-shell";
import { requireOwner } from "@/lib/auth/require-owner";
import { republishVersion } from "@/app/actions";
import { formatDate } from "@/lib/utils";

export const dynamic = "force-dynamic";
type VersionRow = { id: string; toolId: string; version: number; signature: string; publishedAt: string; changelog?: string };

export default async function VersionsPage() {
  const { api } = await requireOwner();
  let data: VersionRow[] = [];
  let error: string | null = null;
  try { data = await api.listToolVersions<VersionRow[]>({ limit: 100 }); }
  catch (cause) { error = cause instanceof Error ? cause.message : "Version history is unavailable."; }

  return <ConsoleShell active="/versions" title="Version Control" subtitle="Rollback republishes a prior recipe as a new immutable version">
    <div className="panel overflow-hidden">
      <div className="border-b border-[#252830] p-4 text-sm">Version ledger</div>
      {error ? <p className="p-4 text-sm text-red-300">{error}</p> : <div className="overflow-x-auto"><table className="w-full text-sm"><thead className="muted text-[10px]"><tr><th className="p-4 text-left">TOOL ID</th><th className="p-4 text-left">VERSION</th><th className="p-4 text-left">SIGNATURE</th><th className="p-4 text-left">PUBLISHED</th><th className="p-4 text-left">ROLLBACK</th></tr></thead><tbody className="divide-y divide-[#252830]">{data.map((version) => <tr key={version.id}><td className="p-4 font-mono text-xs">{version.toolId}</td><td className="p-4">v{version.version}</td><td className="p-4 font-mono text-xs">{version.signature.slice(0, 22)}…</td><td className="muted p-4 text-xs">{formatDate(version.publishedAt)}</td><td className="p-4"><form action={republishVersion} className="flex gap-2"><input type="hidden" name="tool_id" value={version.toolId} /><input type="hidden" name="source_version_id" value={version.id} /><input type="hidden" name="changelog" value={`Rollback based on previous v${version.version}`} /><button className="btn btn-secondary text-xs">Republish as new version</button></form></td></tr>)}</tbody></table></div>}
    </div>
    <p className="muted mt-3 text-[11px]">Rollback never overwrites history. It creates a new version number, validates and signs the previous payload again.</p>
  </ConsoleShell>;
}
