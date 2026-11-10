/**
 * Authenticated client for the Google Cloud Run API.
 *
 * IMPORTANT:
 * - Use this module only from server actions / route handlers.
 * - idToken must be a Firebase Authentication ID token, never a Supabase token.
 * - Cloud Run must independently validate the token and Owner/Member role on every call.
 * - Mutating requests are never retried automatically (avoids duplicate publish/invite/run writes).
 */
export type ApiEnvelope<T> = { data: T };

export type ApiErrorBody = {
  error?: {
    code?: string;
    message?: string;
    details?: unknown;
  };
};

export class CloudRunApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string = "API_ERROR",
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "CloudRunApiError";
  }
}

export type CloudRunClientOptions = {
  baseUrl?: string;
  appCheckToken?: string;
  fetchImpl?: typeof fetch;
};

export type DeviceRegistrationInput = {
  device_id?: string;
  device_label?: string;
  app_version: string;
  android_version: string;
  android_api: number;
  hardware_profile?: Record<string, unknown>;
  telemetry?: Record<string, unknown>;
  field_availability?: Record<string, string>;
};

export type ToolUpsertInput = {
  id?: string;
  name: string;
  slug: string;
  category: string;
  description: string;
  icon?: string;
  draft_recipe: Record<string, unknown>;
};

export type MemberInvitationInput = {
  email: string;
  display_name?: string;
};

export type RepairRunInput = {
  run_id: string;
  device_id: string;
  tool_id: string;
  tool_version: number;
  started_at: string;
  completed_at?: string;
  result: "SUCCESS" | "PARTIAL" | "FAILED" | "CANCELLED";
  step_results: Array<{ step_id: string; status: string; error_code?: string }>;
  error_code?: string;
  app_version: string;
};

const API_PREFIX = "/api/v1";

export function getCloudRunApiBaseUrl(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, "");
  if (!trimmed) throw new Error("MAHI_API_BASE_URL is not configured.");
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error("MAHI_API_BASE_URL must be an absolute HTTPS URL.");
  }
  const localHost = parsed.hostname === "localhost" || parsed.hostname === "127.0.0.1";
  if (parsed.protocol !== "https:" && !(process.env.NODE_ENV !== "production" && localHost && parsed.protocol === "http:")) {
    throw new Error("The Cloud Run API must use HTTPS outside local development.");
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error("MAHI_API_BASE_URL must not contain credentials, query parameters or fragments.");
  }
  return trimmed.endsWith(API_PREFIX) ? trimmed : `${trimmed}${API_PREFIX}`;
}

function readErrorPayload(payload: unknown, fallback: string): { message: string; code: string; details?: unknown } {
  if (payload && typeof payload === "object") {
    const error = (payload as ApiErrorBody).error;
    if (error && typeof error === "object") {
      return {
        message: typeof error.message === "string" && error.message.trim() ? error.message : fallback,
        code: typeof error.code === "string" && error.code.trim() ? error.code : "API_ERROR",
        details: error.details,
      };
    }
  }
  return { message: fallback, code: "API_ERROR" };
}

export class CloudRunApiClient {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;

  constructor(
    private readonly idToken: string,
    options: CloudRunClientOptions = {},
  ) {
    if (!idToken.trim()) throw new Error("A Firebase Authentication ID token is required.");
    const baseUrl = options.baseUrl ?? process.env.MAHI_API_BASE_URL ?? "";
    this.baseUrl = getCloudRunApiBaseUrl(baseUrl);
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.appCheckToken = options.appCheckToken?.trim() || undefined;
  }

  private readonly appCheckToken?: string;

  private async request<T>(
    path: string,
    method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
    body?: unknown,
  ): Promise<T> {
    const headers = new Headers({
      Accept: "application/json",
      Authorization: `Bearer ${this.idToken}`,
      "Cache-Control": "no-store",
    });
    if (body !== undefined) headers.set("Content-Type", "application/json");
    if (this.appCheckToken) headers.set("X-Firebase-AppCheck", this.appCheckToken);

    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        cache: "no-store",
        redirect: "error",
      });
    } catch {
      throw new CloudRunApiError("The repair service could not be reached. Check the connection and retry.", 0, "NETWORK_ERROR");
    }

    const raw = await response.text();
    let payload: unknown = null;
    if (raw) {
      try {
        payload = JSON.parse(raw);
      } catch {
        throw new CloudRunApiError("The repair service returned an invalid response.", response.status, "INVALID_RESPONSE");
      }
    }

    if (!response.ok) {
      const error = readErrorPayload(payload, `Repair service request failed (HTTP ${response.status}).`);
      throw new CloudRunApiError(error.message, response.status, error.code, error.details);
    }
    if (!payload || typeof payload !== "object" || !("data" in payload)) {
      throw new CloudRunApiError("The repair service response is missing its data envelope.", response.status, "INVALID_RESPONSE");
    }
    return (payload as ApiEnvelope<T>).data;
  }

  private id(value: string, label: string): string {
    const normalized = value.trim();
    if (!normalized || normalized.length > 128) throw new Error(`Invalid ${label}.`);
    return encodeURIComponent(normalized);
  }

  verifyIdentity() {
    return this.request<{ userId: string; email: string; role: "owner" | "member"; status: string; isOwner: boolean }>("/auth/verify", "POST", {});
  }

  getMyProfile() {
    return this.request<{ profile: Record<string, unknown>; devices: unknown[] }>("/auth/me");
  }

  registerDevice(input: DeviceRegistrationInput) {
    return this.request<{ device_id: string; registered: boolean }>("/devices/register", "POST", input);
  }

  listDevices<T = Record<string, unknown>[]>() {
    return this.request<T>("/devices");
  }

  revokeDevice(deviceId: string, revoked = true) {
    return this.request<Record<string, unknown>>(`/devices/${this.id(deviceId, "device ID")}/revoke`, "PUT", { revoked });
  }

  listTools<T = Record<string, unknown>[]>() {
    return this.request<T>("/tools");
  }

  getTool<T = Record<string, unknown>>(toolId: string) {
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}`);
  }

  createTool<T = Record<string, unknown>>(input: ToolUpsertInput) {
    return this.request<T>("/tools", "POST", input);
  }

  updateTool<T = Record<string, unknown>>(toolId: string, input: ToolUpsertInput) {
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}`, "PUT", input);
  }

  publishTool<T = Record<string, unknown>>(toolId: string, changelog: string, sourceVersionId?: string) {
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}/publish`, "POST", {
      changelog,
      ...(sourceVersionId ? { source_version_id: sourceVersionId } : {}),
    });
  }

  setToolState<T = Record<string, unknown>>(toolId: string, status: "DRAFT" | "TESTING" | "ARCHIVED") {
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}/state`, "PUT", { status });
  }

  setToolDisabled<T = Record<string, unknown>>(toolId: string, disabled: boolean) {
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}/disable`, "PUT", { disabled });
  }

  assignTool<T = Record<string, unknown>>(toolId: string, audience: "all_active_members" | "selected_members", memberIds: string[] = []) {
    if (audience === "selected_members" && memberIds.length === 0) {
      throw new Error("At least one Member must be selected for a restricted assignment.");
    }
    return this.request<T>(`/tools/${this.id(toolId, "tool ID")}/assign`, "POST", {
      audience,
      ...(audience === "selected_members" ? { member_ids: memberIds } : {}),
    });
  }

  getMyRepairTools<T = Record<string, unknown>[]>(deviceId: string) {
    return this.request<T>("/recipes/my-tools", "POST", { device_id: deviceId });
  }

  getSignedRecipe<T = Record<string, unknown>>(toolId: string, deviceId: string) {
    return this.request<T>("/recipes/signed-tool", "POST", { tool_id: toolId, device_id: deviceId });
  }

  submitRepairRun<T = { run_id: string; accepted: boolean }>(input: RepairRunInput) {
    return this.request<T>("/repair-runs", "POST", input);
  }

  listRepairRuns<T = Record<string, unknown>[]>(query?: { device_id?: string; tool_id?: string; member_id?: string; result?: string; from?: string; to?: string; limit?: number; cursor?: string }) {
    const params = new URLSearchParams();
    if (query?.device_id) params.set("device_id", query.device_id);
    if (query?.tool_id) params.set("tool_id", query.tool_id);
    if (query?.member_id) params.set("member_id", query.member_id);
    if (query?.result) params.set("result", query.result);
    if (query?.from) params.set("from", query.from);
    if (query?.to) params.set("to", query.to);
    if (query?.limit !== undefined) params.set("limit", String(query.limit));
    if (query?.cursor) params.set("cursor", query.cursor);
    const suffix = params.size ? `?${params.toString()}` : "";
    return this.request<T>(`/repair-runs${suffix}`);
  }

  listMembers<T = Record<string, unknown>[]>() {
    return this.request<T>("/members");
  }

  inviteMember<T = Record<string, unknown>>(input: MemberInvitationInput) {
    return this.request<T>("/members/invite", "POST", input);
  }

  setMemberStatus<T = Record<string, unknown>>(memberId: string, status: "active" | "suspended" | "revoked") {
    return this.request<T>(`/members/${this.id(memberId, "Member ID")}/status`, "PUT", { status });
  }

  getAnalytics<T = Record<string, unknown>>() {
    return this.request<T>("/analytics/dashboard");
  }

  getAuditLogs<T = Record<string, unknown>[]>(query?: { limit?: number; cursor?: string }) {
    const params = new URLSearchParams();
    if (query?.limit !== undefined) params.set("limit", String(query.limit));
    if (query?.cursor) params.set("cursor", query.cursor);
    const suffix = params.size ? `?${params.toString()}` : "";
    return this.request<T>(`/audit-logs${suffix}`);
  }


  listToolVersions<T = Record<string, unknown>[]>(query?: { tool_id?: string; limit?: number; cursor?: string }) {
    const params = new URLSearchParams();
    if (query?.tool_id) params.set("tool_id", query.tool_id);
    if (query?.limit !== undefined) params.set("limit", String(query.limit));
    if (query?.cursor) params.set("cursor", query.cursor);
    const suffix = params.size ? `?${params.toString()}` : "";
    return this.request<T>(`/tool-versions${suffix}`);
  }

  listRecipeTests<T = Record<string, unknown>[]>(query?: { limit?: number; cursor?: string }) {
    const params = new URLSearchParams();
    if (query?.limit !== undefined) params.set("limit", String(query.limit));
    if (query?.cursor) params.set("cursor", query.cursor);
    const suffix = params.size ? `?${params.toString()}` : "";
    return this.request<T>(`/recipe-tests${suffix}`);
  }

  listToolAssignments<T = Record<string, unknown>[]>(query?: { tool_id?: string }) {
    const params = new URLSearchParams();
    if (query?.tool_id) params.set("tool_id", query.tool_id);
    const suffix = params.size ? `?${params.toString()}` : "";
    return this.request<T>(`/tool-assignments${suffix}`);
  }

  saveRecipeTest<T = Record<string, unknown>>(input: Record<string, unknown>) {
    return this.request<T>("/recipe-tests", "POST", input);
  }

  deleteDraftTool(toolId: string) {
    return this.request<{ deleted: boolean }>(`/tools/${this.id(toolId, "tool ID")}`, "DELETE");
  }

  sendGeminiInstruction<T = Record<string, unknown>>(instruction: string, confirmed = false) {
    if (!instruction.trim()) throw new Error("An instruction is required.");
    return this.request<T>("/admin/gemini", "POST", { instruction, confirmed });
  }
}
