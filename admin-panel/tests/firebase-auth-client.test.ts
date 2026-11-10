import { afterEach, describe, expect, it, vi } from "vitest";
import { CloudRunAuthClient, refreshFirebaseSession } from "../src/lib/auth/firebase-rest";

const response = (payload: unknown, status = 200) =>
  new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });

const originalApiKey = process.env.FIREBASE_WEB_API_KEY;

afterEach(() => {
  if (originalApiKey === undefined) delete process.env.FIREBASE_WEB_API_KEY;
  else process.env.FIREBASE_WEB_API_KEY = originalApiKey;
  vi.restoreAllMocks();
});

describe("CloudRunAuthClient", () => {
  it("requests an invitation-only OTP through the shared API contract", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(response({
      data: { accepted: true, challenge_id: "challenge-1", expires_at: "2026-11-11T18:00:00Z", resend_after_seconds: 60 },
    }));
    const client = new CloudRunAuthClient({ baseUrl: "https://api.example.com", fetchImpl });

    const result = await client.requestOtp("  OWNER@EXAMPLE.COM ");

    expect(result.challenge_id).toBe("challenge-1");
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("https://api.example.com/api/v1/auth/otp/request");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "owner@example.com" });
  });

  it("verifies the challenge, exchanges the Firebase custom token and returns ID/refresh tokens", async () => {
    process.env.FIREBASE_WEB_API_KEY = "firebase-web-key-for-test";
    const fetchImpl = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response({ data: { firebase_custom_token: "custom-token", expires_in_seconds: 300 } }))
      .mockResolvedValueOnce(response({ idToken: "firebase-id-token", refreshToken: "firebase-refresh-token", expiresIn: "3600", localId: "firebase-uid" }));
    const client = new CloudRunAuthClient({ baseUrl: "https://api.example.com", fetchImpl });

    const result = await client.verifyOtp("owner@example.com", "challenge-1", "123456");

    expect(result).toEqual({
      idToken: "firebase-id-token",
      refreshToken: "firebase-refresh-token",
      expiresInSeconds: 3600,
      localId: "firebase-uid",
    });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(fetchImpl.mock.calls[0][0]).toBe("https://api.example.com/api/v1/auth/otp/verify");
    expect(JSON.parse(String(fetchImpl.mock.calls[0][1]?.body))).toEqual({
      email: "owner@example.com",
      challenge_id: "challenge-1",
      code: "123456",
    });
    expect(String(fetchImpl.mock.calls[1][0])).toContain("accounts:signInWithCustomToken");
    expect(JSON.parse(String(fetchImpl.mock.calls[1][1]?.body))).toEqual({
      token: "custom-token",
      returnSecureToken: true,
    });
  });

  it("rejects malformed email and non-six-digit verification codes before network calls", async () => {
    const fetchImpl = vi.fn<typeof fetch>();
    const client = new CloudRunAuthClient({ baseUrl: "https://api.example.com", fetchImpl });

    await expect(client.requestOtp("not-an-email")).rejects.toThrow(/valid email/);
    await expect(client.verifyOtp("owner@example.com", "challenge-1", "123")).rejects.toThrow(/6-digit/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("refreshes a Firebase session using the server-side refresh token", async () => {
    process.env.FIREBASE_WEB_API_KEY = "firebase-web-key-for-test";
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(response({
      id_token: "next-id-token",
      refresh_token: "next-refresh-token",
      expires_in: "3600",
      user_id: "firebase-user",
    }));
    const result = await refreshFirebaseSession("previous-refresh-token", fetchImpl);

    expect(result.idToken).toBe("next-id-token");
    expect(result.refreshToken).toBe("next-refresh-token");
    expect(result.localId).toBe("firebase-user");
    expect(String(fetchImpl.mock.calls[0][0])).toContain("securetoken.googleapis.com/v1/token");
  });
});
