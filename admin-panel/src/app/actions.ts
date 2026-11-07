"use server";

import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { revalidatePath } from 'next/cache';
import { createClient } from '@/lib/supabase/server';
import { RepairRecipeSchema } from '@/lib/recipe/catalog';
import { requireOwner } from '@/lib/auth/require-owner';

const ADMIN_FUNCTIONS = new Set([
  'admin-invite-member',
  'admin-manage-member',
  'admin-upsert-tool',
  'admin-publish-tool',
  'admin-disable-tool',
  'admin-assign-tool',
  'admin-save-validation',
  'admin-delete-draft-tool',
  'admin-manage-device',
  'admin-manage-tool-state',
  'get-admin-analytics',
]);

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export async function requestOwnerSignInLink(emailInput: string) {
  const email = String(emailInput ?? '').trim().toLowerCase();
  if (email.length > 254 || !EMAIL_PATTERN.test(email)) {
    return { ok: false, message: 'Enter a valid email address.' };
  }

  const requestHeaders = await headers();
  const configuredOrigin = process.env.NEXT_PUBLIC_APP_URL?.trim();
  let origin: string;

  try {
    if (configuredOrigin) {
      const parsed = new URL(configuredOrigin);
      if (
        parsed.username ||
        parsed.password ||
        (parsed.pathname !== '/' && parsed.pathname !== '') ||
        parsed.search ||
        parsed.hash ||
        (process.env.NODE_ENV === 'production' && parsed.protocol !== 'https:')
      ) {
        throw new Error('Invalid application origin.');
      }
      origin = parsed.origin;
    } else if (process.env.NODE_ENV !== 'production') {
      const requestOrigin = requestHeaders.get('origin') ?? requestHeaders.get('referer');
      origin = requestOrigin ? new URL(requestOrigin).origin : 'http://localhost:3000';
    } else {
      return { ok: false, message: 'Owner sign-in is not configured. Contact the administrator.' };
    }
  } catch {
    return { ok: false, message: 'Owner sign-in is not configured. Contact the administrator.' };
  }

  const supabase = await createClient();
  const { error } = await supabase.auth.signInWithOtp({
    email,
    options: {
      shouldCreateUser: false,
      emailRedirectTo: new URL('/auth/callback', origin).toString(),
    },
  });

  if (error) {
    // Do not return provider errors or account-existence details to the browser.
    console.error('Owner sign-in link request failed.');
    return { ok: false, message: 'Unable to start sign-in right now. Please try again later.' };
  }

  return {
    ok: true,
    message: 'If this is an active Owner account, a secure sign-in link has been sent.',
  };
}

export async function signOut() {
  const supabase = await createClient();
  await supabase.auth.signOut({ scope: 'local' });
  revalidatePath('/', 'layout');
  redirect('/login');
}

export async function invokeAdminFunction(functionName: string, body: Record<string, unknown>) {
  if (!ADMIN_FUNCTIONS.has(functionName)) {
    throw new Error('Unsupported administrative operation.');
  }

  const { supabase } = await requireOwner();
  const { data, error } = await supabase.functions.invoke(functionName, { body });
  if (error) throw new Error('The administrative operation could not be completed.');
  return data;
}

export async function createTool(formData: FormData) {
  await requireOwner();
  const name = String(formData.get('name') ?? '').trim();
  const slug = String(formData.get('slug') ?? '').trim();
  const category = String(formData.get('category') ?? 'apps');
  const description = String(formData.get('description') ?? '').trim();
  const icon = String(formData.get('icon') ?? 'wrench');

  let recipe: unknown;
  try {
    recipe = JSON.parse(String(formData.get('recipe') ?? ''));
  } catch {
    throw new Error('Recipe must be valid JSON.');
  }

  const checked = RepairRecipeSchema.safeParse(recipe);
  if (!checked.success) {
    throw new Error(checked.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`).join('\n'));
  }
  if (checked.data.toolId !== slug) throw new Error('Recipe toolId must match the tool slug.');

  await invokeAdminFunction('admin-upsert-tool', {
    name, slug, category, description, icon, draft_recipe: checked.data,
  });
  revalidatePath('/tools');
  redirect('/tools');
}

export async function saveToolDraft(formData: FormData) {
  const id = String(formData.get('id') ?? '');
  const name = String(formData.get('name') ?? '').trim();
  const slug = String(formData.get('slug') ?? '').trim();
  const category = String(formData.get('category') ?? 'apps');
  const description = String(formData.get('description') ?? '').trim();
  const icon = String(formData.get('icon') ?? 'wrench');

  let recipe: unknown;
  try {
    recipe = JSON.parse(String(formData.get('recipe') ?? ''));
  } catch {
    throw new Error('Recipe must be valid JSON.');
  }

  const checked = RepairRecipeSchema.safeParse(recipe);
  if (!checked.success) {
    throw new Error(checked.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`).join('\n'));
  }
  if (checked.data.toolId !== slug) throw new Error('Recipe toolId must match the slug field.');

  const { supabase } = await requireOwner();
  const { data: tool, error } = await supabase.from('repair_tools').select('id,status').eq('id', id).single();
  if (error || !tool) throw new Error('Tool not found.');
  if (tool.status === 'ARCHIVED') throw new Error('Archived tools cannot be edited.');

  await invokeAdminFunction('admin-upsert-tool', {
    id, name, slug, category, description, icon, draft_recipe: checked.data,
  });
  revalidatePath('/tools');
  revalidatePath(`/tools/${id}`);
}

export async function publishTool(formData: FormData) {
  const id = String(formData.get('tool_id') ?? '');
  const changelog = String(formData.get('changelog') ?? '');
  await invokeAdminFunction('admin-publish-tool', { tool_id: id, changelog });
  revalidatePath('/tools');
  revalidatePath('/publishing');
  revalidatePath('/versions');
}

export async function republishVersion(formData: FormData) {
  const toolId = String(formData.get('tool_id') ?? '');
  const sourceVersionId = String(formData.get('source_version_id') ?? '');
  const changelog = String(formData.get('changelog') ?? 'Republished prior version');
  await invokeAdminFunction('admin-publish-tool', {
    tool_id: toolId, source_version_id: sourceVersionId, changelog,
  });
  revalidatePath('/tools');
  revalidatePath('/publishing');
  revalidatePath('/versions');
}

export async function setToolDisabled(formData: FormData) {
  const toolId = String(formData.get('tool_id') ?? '');
  const disabled = String(formData.get('disabled') ?? 'true') === 'true';
  await invokeAdminFunction('admin-disable-tool', { tool_id: toolId, disabled });
  revalidatePath('/tools');
  revalidatePath('/publishing');
}

export async function setToolState(formData: FormData) {
  const toolId = String(formData.get('tool_id') ?? '');
  const status = String(formData.get('status') ?? 'DRAFT');
  await invokeAdminFunction('admin-manage-tool-state', { tool_id: toolId, status });
  revalidatePath('/tools');
  revalidatePath('/publishing');
}

export async function inviteMember(formData: FormData) {
  await requireOwner();
  const email = String(formData.get('email') ?? '').trim().toLowerCase();
  if (email.length > 254 || !EMAIL_PATTERN.test(email)) throw new Error('Enter a valid email address.');
  await invokeAdminFunction('admin-invite-member', { email });
  revalidatePath('/members');
  revalidatePath('/');
}

export async function setMemberStatus(formData: FormData) {
  const userId = String(formData.get('user_id') ?? '');
  const status = String(formData.get('status') ?? 'suspended');
  await invokeAdminFunction('admin-manage-member', { user_id: userId, status });
  revalidatePath('/members');
  revalidatePath('/');
}

export async function assignTool(formData: FormData) {
  const toolId = String(formData.get('tool_id') ?? '');
  const audience = String(formData.get('audience') ?? 'all_active_members');
  const memberIds = formData.getAll('member_ids').map((value) => String(value).trim()).filter(Boolean);
  await invokeAdminFunction('admin-assign-tool', {
    tool_id: toolId,
    audience,
    ...(audience === 'selected_members' ? { member_ids: memberIds } : {}),
  });
  revalidatePath('/tools');
}

export async function saveValidationHistory(formData: FormData) {
  let recipe: unknown;
  const raw = String(formData.get('recipe') ?? '');
  try {
    recipe = JSON.parse(raw);
  } catch {
    recipe = raw;
  }
  await invokeAdminFunction('admin-save-validation', { recipe });
  revalidatePath('/test-lab');
}

export async function duplicateTool(formData: FormData) {
  const id = String(formData.get('tool_id') ?? '');
  const { supabase } = await requireOwner();
  const { data, error } = await supabase
    .from('repair_tools')
    .select('name,slug,category,description,icon,draft_recipe')
    .eq('id', id)
    .single();

  if (error || !data) throw new Error('Tool not found.');
  const slug = `${data.slug}-copy-${crypto.randomUUID().slice(0, 6)}`;
  const recipe = {
    ...(data.draft_recipe as Record<string, unknown>),
    toolId: slug,
    version: 1,
    changelog: 'Duplicated draft',
  };

  await invokeAdminFunction('admin-upsert-tool', {
    name: `${data.name} Copy`,
    slug,
    category: data.category,
    description: data.description,
    icon: data.icon,
    draft_recipe: recipe,
  });
  revalidatePath('/tools');
}

export async function deleteDraftTool(formData: FormData) {
  const id = String(formData.get('tool_id') ?? '');
  await invokeAdminFunction('admin-delete-draft-tool', { tool_id: id });
  revalidatePath('/tools');
}

export async function setDeviceRevoked(formData: FormData) {
  const deviceId = String(formData.get('device_id') ?? '');
  const revoked = String(formData.get('revoked') ?? 'true') === 'true';
  await invokeAdminFunction('admin-manage-device', { device_id: deviceId, revoked });
  revalidatePath('/members');
  revalidatePath('/');
}
