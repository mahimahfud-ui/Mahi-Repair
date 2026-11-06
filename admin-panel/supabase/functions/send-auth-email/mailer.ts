export type EmailActionType =
  | "signup"
  | "invite"
  | "magiclink"
  | "recovery"
  | "email_change"
  | "reauthentication"
  | "email";

export interface AuthEmailHookPayload {
  user: { email?: string | null; new_email?: string | null };
  email_data: {
    token?: string | null;
    token_hash?: string | null;
    token_new?: string | null;
    token_hash_new?: string | null;
    redirect_to?: string | null;
    site_url?: string | null;
    email_action_type: string;
  };
}

export interface PreparedAuthEmail {
  to: string;
  action: EmailActionType;
  subject: string;
  htmlContent: string;
  textContent: string;
  verificationUrl: string | null;
}

export interface MailerConfig {
  supabaseUrl: string;
  resendApiKey: string;
  resendFromEmail: string;
  resendFromName: string;
  brevoApiKey: string;
  brevoFromEmail: string;
  brevoFromName: string;
}

export class ProviderDeliveryError extends Error {
  constructor(
    readonly resendStatus: number | null,
    readonly brevoStatus: number | null,
  ) {
    super("Both authentication email providers failed.");
    this.name = "ProviderDeliveryError";
  }
}

const supportedActions = new Set<EmailActionType>([
  "signup", "invite", "magiclink", "recovery", "email_change", "reauthentication", "email",
]);

const subjects: Record<EmailActionType, string> = {
  signup: "Confirm your Mahi Repair account",
  invite: "Your Mahi Repair invitation",
  magiclink: "Your Mahi Repair sign-in link",
  recovery: "Reset your Mahi Repair access",
  email_change: "Confirm your Mahi Repair email change",
  reauthentication: "Confirm your Mahi Repair sign-in",
  email: "Verify your Mahi Repair email",
};

function requiredEmail(value: string | null | undefined): string {
  const email = value?.trim().toLowerCase() ?? "";
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
    throw new Error("Auth hook payload did not contain a valid recipient email.");
  }
  return email;
}

function escapeHtml(value: string): string {
  const entities: Record<string, string> = {
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  };
  return value.replace(/[&<>"']/g, (character) => entities[character]);
}

function buildVerificationUrl(
  supabaseUrl: string,
  tokenHash: string | null | undefined,
  action: EmailActionType,
  redirectTo: string | null,
): string | null {
  const hash = tokenHash?.trim();
  if (!hash) return null;

  const project = new URL(supabaseUrl);
  const localProject = ["localhost", "127.0.0.1", "[::1]"].includes(project.hostname);
  if (project.protocol !== "https:" && !(project.protocol === "http:" && localProject)) {
    throw new Error("Supabase Auth URL must use HTTPS.");
  }

  const verification = new URL("/auth/v1/verify", project.origin);
  verification.searchParams.set("token", hash);
  verification.searchParams.set("type", action);

  if (redirectTo) {
    const target = new URL(redirectTo);
    const localHttp = target.protocol === "http:" &&
      ["localhost", "127.0.0.1", "[::1]"].includes(target.hostname);
    if (target.protocol !== "https:" && !localHttp) {
      throw new Error("Auth redirect must use HTTPS or a localhost development URL.");
    }
    if (target.username || target.password) {
      throw new Error("Auth redirect must not contain URL credentials.");
    }
    verification.searchParams.set("redirect_to", redirectTo);
  }
  return verification.toString();
}

function renderEmail(
  appName: string,
  action: EmailActionType,
  token: string | null | undefined,
  verificationUrl: string | null,
): { htmlContent: string; textContent: string } {
  const safeName = escapeHtml(appName);
  const cleanToken = token?.trim() || null;
  const safeToken = cleanToken ? escapeHtml(cleanToken) : null;
  const safeUrl = verificationUrl ? escapeHtml(verificationUrl) : null;
  const copy: Record<EmailActionType, string> = {
    signup: "Confirm your email address to activate your Mahi Repair account.",
    invite: "You have been invited to use Mahi Repair. Complete your sign-in to accept the invitation.",
    magiclink: "Use this secure link to sign in to Mahi Repair.",
    recovery: "Use this secure link or one-time code to continue your account recovery.",
    email_change: "Confirm this email change using the link or one-time code below.",
    reauthentication: "Confirm this request to continue your secure Mahi Repair session.",
    email: "Use the link or one-time code below to verify your email address.",
  };

  const linkHtml = safeUrl
    ? '<p style="margin:24px 0"><a href="' + safeUrl +
      '" style="display:inline-block;padding:12px 20px;background:#b91c1c;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600">Continue securely</a></p>'
    : "";
  const codeHtml = safeToken
    ? '<p style="margin:20px 0 6px;color:#52525b">Or enter this one-time code in the Mahi Repair app:</p><p style="font-size:28px;letter-spacing:8px;font-weight:700;color:#18181b">' +
      safeToken + "</p>"
    : "";
  const linkText = verificationUrl ? "Secure link: " + verificationUrl : "";
  const codeText = cleanToken ? "One-time code: " + cleanToken : "";

  return {
    htmlContent:
      '<!doctype html><html><body style="margin:0;padding:32px 16px;background:#f4f4f5;font-family:Arial,sans-serif;color:#18181b">' +
      '<main style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e4e4e7;border-radius:14px;padding:28px">' +
      '<h1 style="font-size:22px;margin:0 0 12px">' + safeName + "</h1>" +
      '<p style="line-height:1.6">' + escapeHtml(copy[action]) + "</p>" +
      linkHtml + codeHtml +
      '<p style="margin-top:28px;font-size:12px;line-height:1.6;color:#71717a">This message was requested from Mahi Repair. If you did not request it, you can ignore this email. Never share your one-time code with anyone.</p>' +
      "</main></body></html>",
    textContent:
      appName + "\n\n" + copy[action] + "\n\n" +
      [linkText, codeText].filter(Boolean).join("\n\n") +
      "\n\nIf you did not request this message, you can ignore it. Never share your one-time code with anyone.",
  };
}

function prepareMessage(
  recipient: string | null | undefined,
  token: string | null | undefined,
  tokenHash: string | null | undefined,
  action: EmailActionType,
  redirectTo: string | null,
  appName: string,
  supabaseUrl: string,
): PreparedAuthEmail {
  const to = requiredEmail(recipient);
  const verificationUrl = buildVerificationUrl(supabaseUrl, tokenHash, action, redirectTo);
  if (!verificationUrl && !token?.trim()) {
    throw new Error("Auth hook payload did not contain a usable one-time code or verification hash.");
  }
  return {
    to,
    action,
    subject: subjects[action],
    ...renderEmail(appName, action, token, verificationUrl),
    verificationUrl,
  };
}

export function buildAuthEmailMessages(
  payload: AuthEmailHookPayload,
  supabaseUrl: string,
  appName = "Mahi Repair",
): PreparedAuthEmail[] {
  if (!payload || !payload.user || !payload.email_data) {
    throw new Error("Invalid Supabase Auth email hook payload.");
  }
  const actionText = payload.email_data.email_action_type;
  if (!supportedActions.has(actionText as EmailActionType)) {
    throw new Error("Unsupported Supabase Auth email action.");
  }
  const action = actionText as EmailActionType;
  const redirectTo = payload.email_data.redirect_to?.trim() ||
    payload.email_data.site_url?.trim() || null;
  const data = payload.email_data;

  if (action === "email_change") {
    const messages: PreparedAuthEmail[] = [];
    // Supabase's secure email-change payload reverses the hash fields.
    if (payload.user.email && data.token_hash_new) {
      messages.push(prepareMessage(
        payload.user.email, data.token, data.token_hash_new, action, redirectTo, appName, supabaseUrl,
      ));
    }
    if (payload.user.new_email && data.token_hash) {
      messages.push(prepareMessage(
        payload.user.new_email, data.token_new || data.token, data.token_hash, action, redirectTo, appName, supabaseUrl,
      ));
    }
    // With secure email change disabled, Auth can supply only the new-address token pair.
    if (messages.length === 0 && data.token_hash) {
      messages.push(prepareMessage(
        payload.user.new_email || payload.user.email,
        data.token_new || data.token,
        data.token_hash,
        action,
        redirectTo,
        appName,
        supabaseUrl,
      ));
    }
    if (messages.length === 0) {
      throw new Error("Email-change hook payload did not contain a usable token recipient pair.");
    }
    return messages;
  }

  return [prepareMessage(
    payload.user.email, data.token, data.token_hash, action, redirectTo, appName, supabaseUrl,
  )];
}

export type Fetcher = typeof fetch;

function idempotencyKey(webhookId: string, index: number): string {
  const safeId = webhookId.replace(/[^a-zA-Z0-9._:-]/g, "_").slice(0, 220);
  if (!safeId) throw new Error("Missing webhook delivery identifier.");
  return (safeId + ":" + index).slice(0, 256);
}

async function sendWithResend(
  email: PreparedAuthEmail,
  config: MailerConfig,
  key: string,
  fetcher: Fetcher,
): Promise<number | null> {
  try {
    const response = await fetcher("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + config.resendApiKey,
        "Content-Type": "application/json",
        "Idempotency-Key": key,
      },
      body: JSON.stringify({
        from: config.resendFromName + " <" + config.resendFromEmail + ">",
        to: [email.to],
        subject: email.subject,
        html: email.htmlContent,
        text: email.textContent,
      }),
      signal: AbortSignal.timeout(1400),
    });
    await response.text();
    return response.ok ? null : response.status;
  } catch {
    return 0;
  }
}

async function sendWithBrevo(
  email: PreparedAuthEmail,
  config: MailerConfig,
  fetcher: Fetcher,
): Promise<number | null> {
  try {
    const response = await fetcher("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": config.brevoApiKey,
        accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        sender: { name: config.brevoFromName, email: config.brevoFromEmail },
        to: [{ email: email.to }],
        subject: email.subject,
        htmlContent: email.htmlContent,
        textContent: email.textContent,
        tags: ["mahi-repair-auth"],
      }),
      signal: AbortSignal.timeout(1400),
    });
    await response.text();
    return response.ok ? null : response.status;
  } catch {
    return 0;
  }
}

export async function sendWithFallback(
  email: PreparedAuthEmail,
  config: MailerConfig,
  webhookId: string,
  messageIndex: number,
  fetcher: Fetcher = fetch,
): Promise<{ provider: "resend" | "brevo"; fallbackUsed: boolean }> {
  const primaryStatus = await sendWithResend(
    email, config, idempotencyKey(webhookId, messageIndex), fetcher,
  );
  if (primaryStatus === null) return { provider: "resend", fallbackUsed: false };

  const fallbackStatus = await sendWithBrevo(email, config, fetcher);
  if (fallbackStatus === null) return { provider: "brevo", fallbackUsed: true };
  throw new ProviderDeliveryError(primaryStatus, fallbackStatus);
}
