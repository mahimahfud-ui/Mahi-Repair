import { notFound } from "next/navigation";
import { ConsoleShell } from "@/components/console-shell";
import { requireOwner } from "@/lib/auth/require-owner";
import { saveToolDraft } from "@/app/actions";
import { RecipeComposer } from "@/components/recipe-composer";

export const dynamic = "force-dynamic";
type ToolDetail = {
  id: string; name: string; slug: string; category: string; description?: string;
  icon?: string; status: string; version: number; draftRecipe: Record<string, unknown>;
};

export default async function ToolEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { api } = await requireOwner();
  let data: ToolDetail;
  try { data = await api.getTool<ToolDetail>(id); }
  catch { notFound(); }

  return <ConsoleShell active="/tools" title={`Edit ${data.name}`} subtitle={`Draft recipe · Current published version v${data.version}`}>
    <RecipeComposer
      mode="edit"
      id={data.id}
      initialName={data.name}
      initialSlug={data.slug}
      initialCategory={data.category}
      initialDescription={data.description ?? ""}
      initialIcon={data.icon ?? "wrench"}
      action={saveToolDraft}
      initialRecipe={data.draftRecipe}
    />
  </ConsoleShell>;
}
