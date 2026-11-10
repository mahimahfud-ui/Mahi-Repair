import { CloudRunApiError, getCloudRunApiBaseUrl } from "../api/cloud-run";

export type FirebaseTokenSet = {
  idToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  localId: string;
};

type OtpAccepted = {
  accepted: true;
  challenge_id: string;
  expires_at: string;
  resend_after_seconds: number;
};

type OtpIssued = {
  firebase_custom_token: string;
  expires_in_seconds: number;
};

export class CloudRunAuthClient {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;

  constructor(options: { baseUrl?: string; fetchImpl?: typeof fetch; appCheckToken?: string } = {}) {
    this.baseUrl = getCloudRunApiBaseUrl(options.baseUrl ?? process.env.MAHI_API_BASE_URL ?? "");
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.appCheckToken = options.appCheckToken?.trim() || undefined;
  }

  private readonly appCheckToken?: string;

  private async post<T>(path: string, body: unknown): Promise<T> {
    const headers = new Headers({ Accept: "application/json", "Content-Type": "application/json" });
    if (this.appCheckToken) headers.set("X-Firebase-AppCheck", this.appCheckToken);
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        cache: "no-store",
        redirect: "error",
      });
    } catch {
      throw new CloudRunApiError("The sign-in service could not be reached. Check the connection and retry.", 0, "NETWORK_ERROR");
    }
    const raw = await response.text();
    let payload: unknown;
    try { payload = raw ? JSON.parse(raw) : null; }
    catch { throw new CloudRunApiError("The sign-in service returned an invalid response.", response.status, "INVALID_RESPONSE"); }
    if (!response.ok) {
      const error = payload && typeof payload === "object" ? (payload as { error?: { code?: string; message?: string } }).error : undefined;
      throw new CloudRunApiError(
        error?.message || `Sign-in request failed (HTTP ${response.status}).`,
        response.status,
        error?.code || "AUTH_ERROR",
      );
    }
    if (!payload || typeof payload !== "object" || !("data" in payload)) {
      throw new CloudRunApiError("The sign-in service returned an incomplete response.", response.status, "INVALID_RESPONSE");
    }
    return (payload as { data: T }).data;
  }

  async requestOtp(email: string): Promise<OtpAccepted> {
    const normalized = email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized) || normalized.length > 254) {
      throw new Error("Enter a valid email address.");
    }
    return this.post<OtpAccepted>("/auth/otp/request", { email: normalized });
  }

  async verifyOtp(email: string, challengeId: string, code: string): Promise<FirebaseTokenSet> {
    const normalized = email.trim().toLowerCase();
    if (!/^[0-9]{6}$/.test(code)) throw new Error("Enter the complete 6-digit verification code.");
    if (!challengeId.trim() || challengeId.length > 256) throw new Error("The sign-in challenge is invalid. Request a new code.");
    const issued = await this.post<OtpIssued>("/auth/otp/verify", {
      email: normalized,
      challenge_id: challengeId,
      code,
    });
    if (!issued.firebase_custom_token) {
      throw new CloudRunApiError("The sign-in service did not return a Firebase custom token.", 502, "INVALID_AUTH_RESPONSE");
    }
    return exchangeFirebaseCustomToken(issued.firebase_custom_token, this.fetchImpl);
  }
}

export async function exchangeFirebaseCustomToken(
  customToken: string,
  fetchImpl: typeof fetch = fetch,
): Promise<FirebaseTokenSet> {
  const apiKey = process.env.FIREBASE_WEB_API_KEY?.trim();
  if (!apiKey) throw new Error("FIREBASE_WEB_API_KEY is not configured on the Admin server.");
  const response = await fetchImpl(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
      cache: "no-store",
      redirect: "error",
    },
  );
  const payload = await response.json().catch(() => null) as
    | { idToken?: string; refreshToken?: string; expiresIn?: string; localId?: string; error?: { message?: string } }
    | null;
  if (!response.ok || !payload?.idToken || !payload.refreshToken || !payload.localId) {
    throw new CloudRunApiError(
      payload?.error?.message || "Firebase could not establish the sign-in session.",
      response.status,
      "FIREBASE_TOKEN_EXCHANGE_FAILED",
    );
  }
  return {
    idToken: payload.idToken,
    refreshToken: payload.refreshToken,
    expiresInSeconds: Number.parseInt(payload.expiresIn || "3600", 10) || 3600,
    localId: payload.localId,
  };
}

export async function refreshFirebaseSession(
  refreshToken: string,
  fetchImpl: typeof fetch = fetch,
): Promise<FirebaseTokenSet> {
  const apiKey = process.env.FIREBASE_WEB_API_KEY?.trim();
  if (!apiKey) throw new Error("FIREBASE_WEB_API_KEY is not configured on the Admin server.");
  const form = new URLSearchParams({ grant_type: "refresh_token", refresh_token: refreshToken });
  const response = await fetchImpl(
    `https://securetoken.googleapis.com/v1/token?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form.toString(),
      cache: "no-store",
      redirect: "error",
    },
  );
  const payload = await response.json().catch(() => null) as
    | { id_token?: string; refresh_token?: string; expires_in?: string; user_id?: string; error?: { message?: string } }
    | null;
  if (!response.ok || !payload?.id_token || !payload.refresh_token || !payload.user_id) {
    throw new CloudRunApiError(
      payload?.error?.message || "Your sign-in session has expired. Please sign in again.",
      response.status,
      "FIREBASE_SESSION_REFRESH_FAILED",
    );
  }
  return {
    idToken: payload.id_token,
    refreshToken: payload.refresh_token,
    expiresInSeconds: Number.parseInt(payload.expires_in || "3600", 10) || 3600,
    localId: payload.user_id,
  };
}
