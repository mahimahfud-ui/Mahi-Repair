import { Webhook } from "npm:standardwebhooks@1.0.0";
import {
  buildAuthEmailMessages,
  ProviderDeliveryError,
  sendWithFallback,
  type AuthEmailHookPayload,
  type MailerConfig,
} from "./mailer.ts";

function hookError(status: number, message: string): Response {
  return new Response(JSON.stringify({ error: { http_code: status, message } }), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error("Missing required Edge Function secret: " + name);
  return value;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return hookError(405, "Only POST requests are supported.");

  const rawHookSecret = Deno.env.get("SEND_EMAIL_HOOK_SECRET")?.trim();
  if (!rawHookSecret) {
    console.error("Auth email hook is missing its webhook secret.");
    return hookError(500, "Authentication email delivery is not configured.");
  }

  const webhookId = request.headers.get("webhook-id");
  if (!webhookId) return hookError(400, "Missing webhook delivery identifier.");

  let body: string;
  try {
    body = await request.text();
  } catch {
    return hookError(400, "Invalid authentication email request.");
  }
  if (!body || new TextEncoder().encode(body).byteLength > 20 * 1024) {
    return hookError(413, "Authentication email request is empty or too large.");
  }

  let payload: AuthEmailHookPayload;
  try {
    const secret = rawHookSecret.replace(/^v1,whsec_/, "");
    const webhook = new Webhook(secret);
    payload = await webhook.verify(body, Object.fromEntries(request.headers.entries())) as AuthEmailHookPayload;
  } catch {
    console.warn("Rejected an Auth email hook request with an invalid signature.");
    return hookError(401, "Invalid Auth email hook signature.");
  }

  const action = payload?.email_data?.email_action_type ?? "unknown";
  try {
    const config: MailerConfig = {
      supabaseUrl: requiredEnv("SUPABASE_URL"),
      resendApiKey: requiredEnv("RESEND_API_KEY"),
      resendFromEmail: requiredEnv("RESEND_FROM_EMAIL"),
      resendFromName: Deno.env.get("RESEND_FROM_NAME")?.trim() || "Mahi Repair",
      brevoApiKey: requiredEnv("BREVO_API_KEY"),
      brevoFromEmail: requiredEnv("BREVO_FROM_EMAIL"),
      brevoFromName: Deno.env.get("BREVO_FROM_NAME")?.trim() || "Mahi Repair",
    };
    const appName = Deno.env.get("AUTH_EMAIL_APP_NAME")?.trim() || "Mahi Repair";
    const messages = buildAuthEmailMessages(payload, config.supabaseUrl, appName);
    const deliveries = await Promise.all(
      messages.map((message, index) => sendWithFallback(message, config, webhookId, index)),
    );

    console.info("Supabase Auth email delivery completed.", {
      action,
      messageCount: deliveries.length,
      providers: deliveries.map((delivery) => delivery.provider),
      fallbackUsed: deliveries.some((delivery) => delivery.fallbackUsed),
    });
    return new Response("{}", {
      status: 200,
      headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
    });
  } catch (error) {
    if (error instanceof ProviderDeliveryError) {
      console.error("Auth email providers rejected delivery.", {
        action,
        resendStatus: error.resendStatus,
        brevoStatus: error.brevoStatus,
      });
      return hookError(503, "Authentication email delivery is temporarily unavailable. Please retry.");
    }
    console.error("Auth email hook configuration or payload error.", {
      action,
      errorType: error instanceof Error ? error.name : "UnknownError",
    });
    return hookError(500, "Authentication email delivery is not configured correctly.");
  }
});
