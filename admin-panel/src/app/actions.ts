"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import {
  FIREBASE_EXPIRES_AT_COOKIE,
  FIREBASE_ID_TOKEN_COOKIE,
  FIREBASE_REFRESH_TOKEN_COOKIE,
} from "@/lib/auth/cookies";
import { requireOwner } from "@/lib/auth/require-owner";
import { RepairRecipeSchema } from "@/lib/recipe/catalog";

const ADMIN_FUNCTIONS = new Set([
  "admin-invite-member",
  "admin-manage-member",
  "admin-upsert-tool",
  "admin-publish-tool",
  "admin-disable-tool",
  "admin-assign-tool",
  "admin-save-validation",
  "admin-delete-draft-tool",
  "admin-manage-device",
  "admin-manage-tool-state",
  "get-admin-analytics",
]);

function requiredString(body: Record<string, unknown>, key: string): string {
  const value = body[key];
  if (typeof value !== "string" || !value.trim()) throw new Error(`Missing required field: ${key}`);
  return value.trim();
}

export async function signOut() {
  const cookieStore = await cookies();
  cookieStore.delete(FIREBASE_ID_TOKEN_COOKIE);
  cookieStore.delete(FIREBASE_REFRESH_TOKEN_COOKIE);
  cookieStore.delete(FIREBASE_EXPIRES_AT_COOKIE);
  redirect("/login");
}

/**
 * Compatibility adapter for existing Admin UI actions.
 * Each operation is mapped to the shared Cloud Run REST contract; Cloud Run independently
 * verifies Firebase ID tokens and the active Owner role. Function names are allowlisted.
 */
export async function invokeAdminFunction(functionName: string, body: Record<string, unknown>) {
  if (!ADMIN_FUNCTIONS.has(functionName)) throw new Error("Unsupported administrative operation.");
  const { api } = await requireOwner();

  switch (functionName) {
    case "admin-invite-member":
      return api.inviteMember({
        email: requiredString(body, "email"),
        ...(typeof body.display_name === "string" ? { display_name: body.display_name } : {}),
      });
    case "admin-manage-member": {
      const status = requiredString(body, "status");
      if (!["active", "suspended", "revoked"].includes(status)) throw new Error("Unsupported Member status.");
      return api.setMemberStatus(requiredString(body, "user_id"), status as "active" | "suspended" | "revoked");
    }
    case "admin-upsert-tool": {
      const input = {
        id: typeof body.id === "string" ? body.id : undefined,
        name: requiredString(body, "name"),
        slug: requiredString(body, "slug"),
        category: requiredString(body, "category"),
        description: typeof body.description === "string" ? body.description : "",
        icon: typeof body.icon === "string" ? body.icon : "wrench",
        draft_recipe: body.draft_recipe as Record<string, unknown>,
      };
      if (!input.draft_recipe || typeof input.draft_recipe !== "object" || Array.isArray(input.draft_recipe)) {
        throw new Error("A valid draft recipe is required.");
      }
      return input.id ? api.updateTool(input.id, input) : api.createTool(input);
    }
    case "admin-publish-tool":
      return api.publishTool(
        requiredString(body, "tool_id"),
        typeof body.changelog === "string" ? body.changelog : "Owner-published release",
        typeof body.source_version_id === "string" ? body.source_version_id : undefined,
      );
    case "admin-disable-tool":
      return api.setToolDisabled(requiredString(body, "tool_id"), body.disabled === true);
    case "admin-assign-tool":
      return api.assignTool(
        requiredString(body, "tool_id"),
        requiredString(body, "audience") as "all_active_members" | "selected_members",
        Array.isArray(body.member_ids) ? body.member_ids.filter((id): id is string => typeof id === "string") : [],
      );
    case "admin-save-validation":
      return api.saveRecipeTest(body);
    case "admin-delete-draft-tool":
      return api.deleteDraftTool(requiredString(body, "tool_id"));
    case "admin-manage-device":
      return api.revokeDevice(requiredString(body, "device_id"), body.revoked !== false);
    case "admin-manage-tool-state": {
      const status = requiredString(body, "status");
      if (!["DRAFT", "TESTING", "ARCHIVED"].includes(status)) throw new Error("Unsupported tool state.");
      return api.setToolState(requiredString(body, "tool_id"), status as "DRAFT" | "TESTING" | "ARCHIVED");
    }
    case "get-admin-analytics":
      return api.getAnalytics();
    default:
      throw new Error("Unsupported administrative operation.");
  }
}

export async function createTool(formData: FormData) {
  const name = String(formData.get("name") ?? "").trim();
  const slug = String(formData.get("slug") ?? "").trim();
  const category = String(formData.get("category") ?? "apps");
  const description = String(formData.get("description") ?? "").trim();
  const icon = String(formData.get("icon") ?? "wrench");
  let recipe: unknown;
  try { recipe = JSON.parse(String(formData.get("recipe") ?? "")); }
  catch { throw new Error("Recipe must be valid JSON."); }
  const checked = RepairRecipeSchema.safeParse(recipe);
  if (!checked.success) throw new Error(checked.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("\n"));
  if (checked.data.toolId !== slug) throw new Error("Recipe toolId must match the tool slug.");
  await invokeAdminFunction("admin-upsert-tool", { name, slug, category, description, icon, draft_recipe: checked.data });
  revalidatePath("/tools");
  redirect("/tools");
}

export async function saveToolDraft(formData: FormData) {
  const id = String(formData.get("id") ?? "").trim();
  const name = String(formData.get("name") ?? "").trim();
  const slug = String(formData.get("slug") ?? "").trim();
  const category = String(formData.get("category") ?? "apps");
  const description = String(formData.get("description") ?? "").trim();
  const icon = String(formData.get("icon") ?? "wrench");
  let recipe: unknown;
  try { recipe = JSON.parse(String(formData.get("recipe") ?? "")); }
  catch { throw new Error("Recipe must be valid JSON."); }
  const checked = RepairRecipeSchema.safeParse(recipe);
  if (!checked.success) throw new Error(checked.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("\n"));
  if (checked.data.toolId !== slug) throw new Error("Recipe toolId must match the tool slug.");
  const { api } = await requireOwner();
  const tool = await api.getTool<{ id: string; status: string }>(id);
  if (tool.status === "ARCHIVED") throw new Error("Archived tools cannot be edited.");
  await invokeAdminFunction("admin-upsert-tool", { id, name, slug, category, description, icon, draft_recipe: checked.data });
  revalidatePath("/tools");
  revalidatePath(`/tools/${id}`);
}

export async function publishTool(formData: FormData) {
  await invokeAdminFunction("admin-publish-tool", {
    tool_id: String(formData.get("tool_id") ?? ""),
    changelog: String(formData.get("changelog") ?? ""),
  });
  revalidatePath("/tools"); revalidatePath("/publishing"); revalidatePath("/versions");
}

export async function republishVersion(formData: FormData) {
  await invokeAdminFunction("admin-publish-tool", {
    tool_id: String(formData.get("tool_id") ?? ""),
    source_version_id: String(formData.get("source_version_id") ?? ""),
    changelog: String(formData.get("changelog") ?? "Republished prior version"),
  });
  revalidatePath("/tools"); revalidatePath("/publishing"); revalidatePath("/versions");
}

export async function setToolDisabled(formData: FormData) {
  await invokeAdminFunction("admin-disable-tool", {
    tool_id: String(formData.get("tool_id") ?? ""),
    disabled: String(formData.get("disabled") ?? "true") === "true",
  });
  revalidatePath("/tools"); revalidatePath("/publishing");
}

export async function setToolState(formData: FormData) {
  await invokeAdminFunction("admin-manage-tool-state", {
    tool_id: String(formData.get("tool_id") ?? ""),
    status: String(formData.get("status") ?? "DRAFT"),
  });
  revalidatePath("/tools"); revalidatePath("/publishing");
}

export async function inviteMember(formData: FormData) {
  await invokeAdminFunction("admin-invite-member", { email: String(formData.get("email") ?? "").trim() });
  revalidatePath("/members");
}

export async function setMemberStatus(formData: FormData) {
  await invokeAdminFunction("admin-manage-member", {
    user_id: String(formData.get("user_id") ?? ""),
    status: String(formData.get("status") ?? "suspended"),
  });
  revalidatePath("/members");
}

export async function assignTool(formData: FormData) {
  const audience = String(formData.get("audience") ?? "all_active_members");
  await invokeAdminFunction("admin-assign-tool", {
    tool_id: String(formData.get("tool_id") ?? ""),
    audience,
    ...(audience === "selected_members" ? {
      member_ids: formData.getAll("member_ids").map((value) => String(value).trim()).filter(Boolean),
    } : {}),
  });
  revalidatePath("/tools");
}

export async function saveValidationHistory(formData: FormData) {
  const raw = String(formData.get("recipe") ?? "");
  let recipe: unknown;
  try { recipe = JSON.parse(raw); } catch { recipe = raw; }
  await invokeAdminFunction("admin-save-validation", { recipe });
  revalidatePath("/test-lab");
}

export async function duplicateTool(formData: FormData) {
  const id = String(formData.get("tool_id") ?? "");
  const { api } = await requireOwner();
  const data = await api.getTool<{
    name: string; slug: string; category: string; description: string; icon?: string; draftRecipe: Record<string, unknown>;
  }>(id);
  const slug = `${data.slug}-copy-${crypto.randomUUID().slice(0, 6)}`;
  const recipe = { ...data.draftRecipe, toolId: slug, version: 1, changelog: "Duplicated draft" };
  await invokeAdminFunction("admin-upsert-tool", {
    name: `${data.name} Copy`, slug, category: data.category, description: data.description,
    icon: data.icon || "wrench", draft_recipe: recipe,
  });
  revalidatePath("/tools");
}

export async function deleteDraftTool(formData: FormData) {
  await invokeAdminFunction("admin-delete-draft-tool", { tool_id: String(formData.get("tool_id") ?? "") });
  revalidatePath("/tools");
}

export async function setDeviceRevoked(formData: FormData) {
  await invokeAdminFunction("admin-manage-device", {
    device_id: String(formData.get("device_id") ?? ""),
    revoked: String(formData.get("revoked") ?? "true") === "true",
  });
  revalidatePath("/members");
}
