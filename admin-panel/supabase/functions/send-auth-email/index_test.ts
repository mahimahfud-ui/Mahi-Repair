import {
  buildAuthEmailMessages,
  sendWithFallback,
  type AuthEmailHookPayload,
  type MailerConfig,
} from "./mailer.ts";

const config: MailerConfig = {
  supabaseUrl: "https://project-ref.supabase.co",
  resendApiKey: "test-resend-key",
  resendFromEmail: "auth@example.com",
  resendFromName: "Mahi Repair",
  brevoApiKey: "test-brevo-key",
  brevoFromEmail: "auth@example.com",
  brevoFromName: "Mahi Repair",
};

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function basePayload(overrides: Partial<AuthEmailHookPayload["email_data"]> = {}): AuthEmailHookPayload {
  return {
    user: { email: "owner@example.com" },
    email_data: {
      token: "123456",
      token_hash: "test-token-hash",
      redirect_to: "https://admin.example.com/auth/callback",
      site_url: "https://admin.example.com",
      email_action_type: "magiclink",
      ...overrides,
    },
  };
}

Deno.test("Admin Magic Link preserves Auth redirect and includes an OTP code", () => {
  const [email] = buildAuthEmailMessages(basePayload(), config.supabaseUrl);
  assert(email.to === "owner@example.com", "recipient email changed");
  assert(email.verificationUrl, "verification link was not built");
  const link = new URL(email.verificationUrl);
  assert(link.origin === config.supabaseUrl, "wrong Supabase project");
  assert(link.pathname === "/auth/v1/verify", "wrong verification endpoint");
  assert(link.searchParams.get("token") === "test-token-hash", "token hash changed");
  assert(link.searchParams.get("type") === "magiclink", "Auth action type changed");
  assert(link.searchParams.get("redirect_to") === "https://admin.example.com/auth/callback", "Admin callback redirect changed");
  assert(email.textContent.includes("One-time code: 123456"), "Android OTP code is missing");
  assert(email.htmlContent.includes("Continue securely"), "clickable Admin link is missing");
});

Deno.test("Android OTP uses the project site URL when no client redirect is supplied", () => {
  const [email] = buildAuthEmailMessages(basePayload({
    redirect_to: "",
    site_url: "https://repair.example.com",
  }), config.supabaseUrl);
  assert(email.textContent.includes("One-time code: 123456"), "six-digit app OTP was not delivered");
  assert(email.verificationUrl, "verification link was not built");
  assert(new URL(email.verificationUrl).searchParams.get("redirect_to") === "https://repair.example.com", "Android redirect changed");
});

Deno.test("secure email change uses Supabase token-hash reversal", () => {
  const payload: AuthEmailHookPayload = {
    user: { email: "old@example.com", new_email: "new@example.com" },
    email_data: {
      token: "111111",
      token_hash_new: "hash-for-old",
      token_new: "222222",
      token_hash: "hash-for-new",
      email_action_type: "email_change",
      redirect_to: "https://admin.example.com/auth/callback",
    },
  };
  const messages = buildAuthEmailMessages(payload, config.supabaseUrl);
  assert(messages.length === 2, "both email addresses must be notified");
  assert(messages[0].to === "old@example.com", "current email recipient changed");
  assert(new URL(messages[0].verificationUrl!).searchParams.get("token") === "hash-for-old", "current email hash mapping changed");
  assert(messages[0].textContent.includes("111111"), "current email OTP is missing");
  assert(messages[1].to === "new@example.com", "new email recipient changed");
  assert(new URL(messages[1].verificationUrl!).searchParams.get("token") === "hash-for-new", "new email hash mapping changed");
  assert(messages[1].textContent.includes("222222"), "new email OTP is missing");
});

Deno.test("unsafe redirect protocols are rejected", () => {
  let rejected = false;
  try {
    buildAuthEmailMessages(basePayload({ redirect_to: "javascript:alert(1)" }), config.supabaseUrl);
  } catch {
    rejected = true;
  }
  assert(rejected, "unsafe redirect was accepted");
});

Deno.test("events without a usable OTP or token hash are rejected", () => {
  let rejected = false;
  try {
    buildAuthEmailMessages(basePayload({ token: "", token_hash: "" }), config.supabaseUrl);
  } catch {
    rejected = true;
  }
  assert(rejected, "empty authentication email was accepted");
});

Deno.test("Resend is primary and Brevo is used only when Resend fails", async () => {
  const [email] = buildAuthEmailMessages(basePayload(), config.supabaseUrl);
  const calls: string[] = [];
  const mockFetch: typeof fetch = async (input) => {
    calls.push(String(input));
    if (calls.length === 1) return new Response("temporary error", { status: 503 });
    return new Response(JSON.stringify({ messageId: "test-id" }), { status: 201 });
  };
  const result = await sendWithFallback(email, config, "webhook-event-1", 0, mockFetch);
  assert(result.provider === "brevo", "fallback provider did not deliver");
  assert(result.fallbackUsed, "fallback use was not recorded");
  assert(calls.length === 2, "unexpected provider call count");
  assert(calls[0] === "https://api.resend.com/emails", "Resend was not tried first");
  assert(calls[1] === "https://api.brevo.com/v3/smtp/email", "Brevo was not tried second");
});

Deno.test("successful Resend delivery does not contact Brevo", async () => {
  const [email] = buildAuthEmailMessages(basePayload(), config.supabaseUrl);
  let calls = 0;
  const mockFetch: typeof fetch = async () => {
    calls += 1;
    return new Response(JSON.stringify({ id: "resend-test-id" }), { status: 200 });
  };
  const result = await sendWithFallback(email, config, "webhook-event-2", 0, mockFetch);
  assert(result.provider === "resend", "Resend was not the primary provider");
  assert(!result.fallbackUsed, "fallback should not be used after primary success");
  assert(calls === 1, "Brevo was contacted despite primary success");
});
