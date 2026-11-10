import { afterEach, describe, expect, it, vi } from "vitest";
import { CloudRunApiClient, CloudRunApiError } from "../src/lib/api/cloud-run";

const jsonResponse = (payload: unknown, status = 200) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });

describe("CloudRunApiClient", () => {
  afterEach(() => vi.restoreAllMocks());

  it("requires a Firebase ID token", () => {
    expect(() => new CloudRunApiClient(" ", { baseUrl: "https://api.example.com" })).toThrow(/Firebase Authentication ID token/);
  });

  it("normalizes the API prefix and sends bearer, App Check and JSON headers", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ data: { userId: "uid-1", role: "owner" } }));
    const client = new CloudRunApiClient("firebase-id-token", {
      baseUrl: "https://api.example.com/api/v1/",
      appCheckToken: "app-check-token",
      fetchImpl,
    });

    const result = await client.verifyIdentity();

    expect(result).toEqual({ userId: "uid-1", role: "owner" });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe("https://api.example.com/api/v1/auth/verify");
    expect(init?.method).toBe("POST");
    const headers = new Headers(init?.headers);
    expect(headers.get("Authorization")).toBe("Bearer firebase-id-token");
    expect(headers.get("X-Firebase-AppCheck")).toBe("app-check-token");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(init?.body))).toEqual({});
    expect(init?.cache).toBe("no-store");
    expect(init?.redirect).toBe("error");
  });

  it("rejects insecure production API URLs", () => {
    expect(() => new CloudRunApiClient("token", { baseUrl: "http://api.example.com" })).toThrow(/HTTPS/);
  });

  it("allows localhost HTTP only outside production", () => {
    expect(() => new CloudRunApiClient("token", { baseUrl: "http://localhost:8080" })).not.toThrow();
  });

  it("surfaces structured API errors without losing status and code", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({ error: { code: "FORBIDDEN_OWNER_REQUIRED", message: "Owner access required." } }, 403),
    );
    const client = new CloudRunApiClient("member-token", { baseUrl: "https://api.example.com", fetchImpl });

    const result = client.getAnalytics();

    await expect(result).rejects.toMatchObject({
      name: "CloudRunApiError",
      message: "Owner access required.",
      status: 403,
      code: "FORBIDDEN_OWNER_REQUIRED",
    });
  });

  it("does not retry a mutating request after a server failure", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({ error: { code: "INTERNAL", message: "Publish failed." } }, 500),
    );
    const client = new CloudRunApiClient("owner-token", { baseUrl: "https://api.example.com", fetchImpl });

    await expect(client.publishTool("tool-1", "release")).rejects.toBeInstanceOf(CloudRunApiError);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("rejects malformed success envelopes", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ ok: true }));
    const client = new CloudRunApiClient("owner-token", { baseUrl: "https://api.example.com", fetchImpl });

    await expect(client.getAnalytics()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });

  it("requires a selected Member for restricted assignments", async () => {
    const fetchImpl = vi.fn<typeof fetch>();
    const client = new CloudRunApiClient("owner-token", { baseUrl: "https://api.example.com", fetchImpl });

    await expect(client.assignTool("tool-1", "selected_members", [])).rejects.toThrow(/At least one Member/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
