import { createServerClient } from "@supabase/ssr";
import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { createClient as createRepairClient } from "@/lib/supabase/server";
import { parseAdminAiRequest, MAHI_REPAIR_AI_INSTRUCTIONS } from "@/lib/mahi-ai-request";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const OWNER_EMAIL = (process.env.OWNER_EMAIL || "mahinmhfd@gmail.com").trim().toLowerCase();
const MAHICLOUD_URL = (process.env.MAHICLOUD_SUPABASE_URL || "https://husydmiujigyonlxbuuj.supabase.co").trim().replace(/\/$/, "");
// Publishable key only. Provider API keys remain inside the MahiCloud Supabase Edge Function secrets.
const MAHICLOUD_PUBLISHABLE_KEY =
  (process.env.MAHICLOUD_SUPABASE_PUBLISHABLE_KEY || "sb_publishable_zzAitNIGOutgaS5EGZKfrQ_osRrlu8U").trim();

const requestWindows = new Map<string, number[]>();
function allowRequest(userId: string): boolean {
  const now = Date.now();
  const recent = (requestWindows.get(userId) || []).filter((timestamp) => now - timestamp < 60_000);
  if (recent.length >= 12) {
    requestWindows.set(userId, recent);
    return false;
  }
  recent.push(now);
  requestWindows.set(userId, recent);
  if (requestWindows.size > 500) {
    for (const [id, timestamps] of requestWindows) {
      if (!timestamps.some((timestamp) => now - timestamp < 60_000)) requestWindows.delete(id);
    }
  }
  return true;
}

function jsonError(status: number, message: string) {
  return NextResponse.json({ error: message }, { status, headers: { "Cache-Control": "no-store" } });
}

export async function POST(request: Request) {
  try {
    const repairSupabase = await createRepairClient();
    const { data: { user }, error: authError } = await repairSupabase.auth.getUser();
    if (authError || !user) return jsonError(401, "Sign in to the Owner Console first.");

    if ((user.email || "").toLowerCase() !== OWNER_EMAIL) {
      return jsonError(403, "Only the configured Owner can use the AI recipe assistant.");
    }

    const { data: profile, error: profileError } = await repairSupabase
      .from("profiles")
      .select("role,status")
      .eq("id", user.id)
      .maybeSingle();
    if (profileError || !profile || profile.role !== "owner" || profile.status !== "active") {
      return jsonError(403, "An active Owner profile is required.");
    }

    if (!allowRequest(user.id)) return jsonError(429, "AI request limit reached. Please wait one minute.");

    let body: unknown;
    try { body = await request.json(); }
    catch { return jsonError(400, "The request body must be valid JSON."); }

    let input: ReturnType<typeof parseAdminAiRequest>;
    try { input = parseAdminAiRequest(body); }
    catch (error) { return jsonError(422, error instanceof Error ? error.message : "Invalid AI request."); }

    if (!MAHICLOUD_URL.startsWith("https://") || !MAHICLOUD_PUBLISHABLE_KEY.startsWith("sb_publishable_")) {
      return jsonError(503, "The MahiCloud AI connection is not configured.");
    }

    const cookieStore = await cookies();
    const mahiCloud = createServerClient(MAHICLOUD_URL, MAHICLOUD_PUBLISHABLE_KEY, {
      cookieOptions: {
        name: "mahi_repair_mahicloud_ai",
        path: "/",
        sameSite: "lax",
        secure: process.env.NODE_ENV === "production",
        httpOnly: true,
      },
      cookies: {
        getAll: () => cookieStore.getAll(),
        setAll: (values) => {
          try {
            values.forEach(({ name, value, options }) => cookieStore.set(name, value, options));
          } catch {
            // Keep server rendering safe when cookies are read-only.
          }
        },
      },
    });

    const { data: { user: aiUser }, error: aiAuthError } = await mahiCloud.auth.getUser();
    if (aiAuthError || !aiUser?.is_anonymous) {
      await mahiCloud.auth.signOut();
      const { data, error } = await mahiCloud.auth.signInAnonymously();
      if (error || !data.user?.is_anonymous) {
        return jsonError(503, "MahiCloud could not create a private AI session. Check anonymous sign-in settings for that Supabase project.");
      }
    }

    const { data: { session }, error: sessionError } = await mahiCloud.auth.getSession();
    if (sessionError || !session?.access_token) {
      return jsonError(503, "MahiCloud AI session is unavailable. Please retry.");
    }

    const upstream = await fetch(MAHICLOUD_URL + "/functions/v1/mahi-ai-chat", {
      method: "POST",
      cache: "no-store",
      signal: request.signal,
      headers: {
        "apikey": MAHICLOUD_PUBLISHABLE_KEY,
        "Authorization": "Bearer " + session.access_token,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        temporary_chat: true,
        think_mode: input.thinkMode,
        response_style: "detailed",
        custom_instructions: MAHI_REPAIR_AI_INSTRUCTIONS,
        messages: input.messages,
      }),
    });

    if (!upstream.ok) {
      const errorBody = await upstream.text();
      return new Response(errorBody || JSON.stringify({ error: "The MahiCloud AI request failed." }), {
        status: upstream.status,
        headers: {
          "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
          "Cache-Control": "no-store",
        },
      });
    }

    if (!upstream.body) return jsonError(502, "The AI service returned no response stream.");
    return new Response(upstream.body, {
      status: 200,
      headers: {
        "Content-Type": upstream.headers.get("content-type") || "text/event-stream; charset=utf-8",
        "Cache-Control": "no-store, no-transform",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
      },
    });
  } catch (error) {
    console.error("Mahi Repair AI route failed:", error instanceof Error ? error.message : "unknown error");
    return jsonError(500, "The AI assistant is temporarily unavailable. Please retry.");
  }
}
