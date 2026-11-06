import { requireOwner, respond, preflight, handleError, jsonBody, rateLimit, env } from '../_shared/http.ts';
import { z } from 'npm:zod@4';

const Input = z.object({
  email: z.string().email().max(254),
  redirect_to: z.string().url().optional(),
}).strict();

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return preflight(req);
  try {
    const { user, admin } = await requireOwner(req);
    await rateLimit(admin, `invite:${user.id}`, 10, 3600);

    const parsed = Input.safeParse(await jsonBody(req));
    if (!parsed.success) {
      return respond(req, { error: { code: 'INVALID_INVITATION', issues: parsed.error.issues } }, 422);
    }

    const email = parsed.data.email.trim().toLowerCase();
    const allowed = env('ADMIN_ORIGIN');
    let redirectTo = allowed;
    if (parsed.data.redirect_to) {
      try {
        if (new URL(parsed.data.redirect_to).origin !== new URL(allowed).origin) {
          throw new Error('Origin mismatch');
        }
        redirectTo = parsed.data.redirect_to;
      } catch {
        throw Object.assign(
          new Error('Invitation redirect must use the configured admin origin.'),
          { status: 400 },
        );
      }
    }

    // Supabase Auth invitation is an external side effect. The profile reconciliation
    // and immutable audit row below are committed together by a service-only RPC.
    const { data, error } = await admin.auth.admin.inviteUserByEmail(email, {
      redirectTo,
      data: { role: 'member' },
    });
    if (error) throw error;
    if (!data.user) throw new Error('Invitation did not return a user record.');

    const { error: profileError } = await admin.rpc('admin_record_invited_member', {
      p_actor_id: user.id,
      p_member_id: data.user.id,
      p_email: email,
    });
    if (profileError) throw profileError;

    return respond(req, { data: { user_id: data.user.id, invited: true } });
  } catch (error) {
    return handleError(req, error);
  }
});
