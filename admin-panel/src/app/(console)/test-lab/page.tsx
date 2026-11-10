import { ConsoleShell } from "@/components/console-shell";
import { TestLabClient } from "@/components/test-lab-client";
import { requireOwner } from "@/lib/auth/require-owner";

export const dynamic = "force-dynamic";
type TestRow = { id: string; recipeHash: string; validationStatus: string; simulatedStepCount: number; testedAt: string; issues: unknown[] };

export default async function TestLabPage() {
  const { api } = await requireOwner();
  let history: Array<{ id: string; recipe_hash: string; validation_status: string; simulated_step_count: number; tested_at: string; issues: unknown[] }> = [];
  try {
    const rows = await api.listRecipeTests<TestRow[]>({ limit: 12 });
    history = rows.map((row) => ({
      id: row.id,
      recipe_hash: row.recipeHash,
      validation_status: row.validationStatus,
      simulated_step_count: row.simulatedStepCount,
      tested_at: row.testedAt,
      issues: row.issues || [],
    }));
  } catch {
    // The test editor remains available even when no history can be loaded.
  }
  return <ConsoleShell active="/test-lab" title="Test Lab" subtitle="Static validation and dry-run preview — not real-device testing"><TestLabClient history={history} /></ConsoleShell>;
}
