# Mahi Repair Cloud Run API Contract v2

This is the normative request/response contract for the Android app and canonical `admin-panel/` website. The existing root `openapi.json` is a legacy staging outline; do not implement against its incomplete response schemas. Cloud Run must implement these routes and keep response shapes stable.

## Base URL and shared transport

- Base: `https://<cloud-run-service>/api/v1`; Admin website reads it from server-only `MAHI_API_BASE_URL`.
- HTTPS only outside localhost development.
- Authenticated calls: `Authorization: Bearer <Firebase ID token>`.
- Android calls must also send `X-Firebase-AppCheck: <valid App Check token>`; verify it server-side.
- JSON requests: `Content-Type: application/json`; all responses: JSON.
- Success envelope: `{ "data": ... }`.
- Error envelope: `{ "error": { "code": "...", "message": "...", "details"?: {}, "request_id"?: "..." } }`.
- Never log access tokens, OTPs, custom tokens, passwords, email provider credentials, Gemini keys or RSA private key material.
- Do not automatically retry writes. Clients must reuse explicit idempotency keys where the API supports them.

## Authentication contract

### Request OTP

`POST /auth/otp/request` (public; abuse controls + App Check on Android)

Request:
```json
{ "email": "member@example.com" }
```

Accepted response:
```json
{
  "data": {
    "accepted": true,
    "challenge_id": "opaque-random-challenge-id",
    "expires_at": "2026-11-11T18:30:00Z",
    "resend_after_seconds": 60
  }
}
```

Only pre-provisioned active Owner/Member profiles can complete sign-in. Never create a user/profile from this endpoint. Use a generic response to prevent account enumeration. Store only a hash of a cryptographically random OTP; bind it to the challenge and normalized email; expire quickly; enforce a low attempt limit, 60-second resend cooldown, per-email/IP/global rate limits, single-use consumption and audit of abuse without storing the code.

### Verify OTP and exchange token

`POST /auth/otp/verify` (public; challenge + attempt limits)

Request:
```json
{ "email": "member@example.com", "challenge_id": "opaque-random-challenge-id", "code": "123456" }
```

Response:
```json
{ "data": { "firebase_custom_token": "<short-lived-firebase-custom-token>", "expires_in_seconds": 300 } }
```

The client must exchange this with Firebase Auth SDK using `signInWithCustomToken`, then obtain a Firebase ID token. Never return a Supabase JWT and never create or promote accounts here.

### Verify Firebase identity

`POST /auth/verify` — Firebase ID token required; empty JSON body.

```json
{
  "data": {
    "userId": "firebase-auth-uid",
    "email": "verified@example.com",
    "role": "owner",
    "status": "active",
    "isOwner": true
  }
}
```

Role/status must be loaded from a server-managed profile after Firebase ID token verification, not from request fields, custom client claims or email alone.

`GET /auth/me` returns `{ "data": { "profile": { ... }, "devices": [ ... ] } }`.

## Fixed identity invariants

- `mahinmhfd@gmail.com` is the only permitted Owner; active profile role must be `owner`.
- `mahimahfud@gmail.com` remains a Member; never promote, bootstrap or assign Owner claims.
- Every other invited account is a Member. Owner bootstrap is one-time, server-only and audited.
- Suspended/revoked profiles fail closed. Invites must not accept a role field that can create an Owner.
- Migration must map Firebase UIDs to verified identities explicitly; never assume Supabase and Firebase UIDs are identical.

## Devices

| Method and path | Role | Request | Success data |
|---|---|---|---|
| `POST /devices/register` | Member | `device_id?`, `device_label?`, `app_version`, `android_version`, `android_api`, optional consented `hardware_profile`, `telemetry`, `field_availability` | `{ device_id, registered }` |
| `GET /devices` | Owner / Member | None | Owner gets all; Member gets only their own |
| `PUT /devices/{id}/revoke` | Owner | `{ revoked: true|false }` | Updated device |

Device IDs must be server-issued UUIDs. Bind every device to the authenticated Firebase UID. A revoked device cannot re-register itself with the old ID. Telemetry must be limited to the consented fields in the device profile schema.

## Tools, signing and assignment

| Method and path | Role | Request |
|---|---|---|
| `GET /tools` | Owner / Member | None; Member only sees published, assigned and compatible tools |
| `POST /tools` | Owner | ToolUpsert below; creates DRAFT |
| `GET /tools/{id}` | Owner / authorized Member | None |
| `PUT /tools/{id}` | Owner | ToolUpsert below; only editable drafts |
| `POST /tools/{id}/publish` | Owner | `{ changelog, source_version_id? }` |
| `PUT /tools/{id}/state` | Owner | `{ status: "DRAFT"|"TESTING"|"ARCHIVED" }` |
| `PUT /tools/{id}/disable` | Owner | `{ disabled: boolean }` |
| `POST /tools/{id}/assign` | Owner | `{ audience:"all_active_members" }` or `{ audience:"selected_members", member_ids:[...] }` |

ToolUpsert:
```json
{
  "id": "optional-existing-tool-id",
  "name": "Network diagnostics",
  "slug": "network-diagnostics",
  "category": "network",
  "description": "Guided network checks",
  "icon": "wifi",
  "draft_recipe": {
    "schemaVersion": 1,
    "toolId": "network-diagnostics",
    "title": "Network diagnostics",
    "version": 1,
    "category": "network",
    "minAndroidApi": 26,
    "minAppVersion": "1.0.0",
    "estimatedSeconds": 30,
    "steps": [
      { "id": "check-internet", "action": "CHECK_INTERNET", "params": {}, "risk": "read_only", "requiresConfirmation": false }
    ]
  }
}
```

The actual recipe must validate against `admin-panel/schemas/repair-recipe-v1.schema.json`; the example above is illustrative only. Reject unknown actions, extra/invalid parameters, unsafe or unsupported schema versions, duplicate step IDs and incompatible device/app requirements.

Publication is an atomic server operation:
1. Validate recipe and compatibility.
2. Canonicalize the exact JSON bytes with the same deterministic algorithm used by Android verification tests.
3. Sign those bytes server-side using RSA-3072 / SHA-256; private key is Secret Manager only.
4. Persist immutable version metadata and exact signed payload plus signature, `signature_algorithm="RSA-SHA256"`, `key_id`, publisher UID and timestamp.
5. Update catalog version and append an audit event in the same transaction/atomic commit.
6. Return the signed version. Never accept client-generated signatures, and never modify a previously published version.

## Member recipe delivery

| Method and path | Role | Request |
|---|---|---|
| `POST /recipes/my-tools` | Member | `{ device_id }` |
| `POST /recipes/signed-tool` | Member | `{ tool_id, device_id }` |

Signed tool response data:
```json
{
  "tool_id": "server-tool-uuid",
  "version": 3,
  "signed_payload": "{...exact canonical UTF-8 JSON...}",
  "signature": "<base64>",
  "signature_algorithm": "RSA-SHA256",
  "key_id": "repair-signing-2026-01",
  "published_at": "2026-11-11T18:00:00Z"
}
```

Android verifies the signature over the exact UTF-8 bytes before parsing; checks key ID, schema, device authorization and native action parameters; then executes only locally implemented allowlisted actions. A notification is only a sync hint, never permission to execute. Require fresh online authorization before an execution when immediate revocation is a requirement.

## Repair runs and reporting

`POST /repair-runs` requires an authenticated Member and a server-authorized device/tool/version. Body:
```json
{
  "run_id": "uuid-idempotency-key",
  "device_id": "registered-device-uuid",
  "tool_id": "tool-uuid",
  "tool_version": 3,
  "started_at": "2026-11-11T18:00:00Z",
  "completed_at": "2026-11-11T18:00:25Z",
  "result": "SUCCESS",
  "step_results": [{ "step_id": "check-internet", "status": "SUCCESS" }],
  "app_version": "1.0.0"
}
```

Success data: `{ "run_id": "...", "accepted": true }`. Enforce idempotency on `run_id`; a retry returns the original acknowledgement and does not create a duplicate record. Owner can query all runs; Member can query their own only. Bound page sizes, filters and telemetry.

## Owner operations

| Method and path | Contract |
|---|---|
| `GET /members` | Owner only; return Member profiles only |
| `POST /members/invite` | Owner only; body `{ email, display_name? }`; always create role=member |
| `PUT /members/{id}/status` | Owner only; body `{ status:"active"|"suspended"|"revoked" }` |
| `GET /analytics/dashboard` | Owner only; aggregate real persistent metrics |
| `GET /audit-logs?limit=&cursor=` | Owner only; append-only, paginated events |
| `POST /admin/gemini` | Owner only; body `{ instruction, confirmed? }`; constrained tool allowlist and confirmation gate |

Gemini must never receive raw service credentials or signing keys, execute arbitrary SQL/shell/code, or bypass the same Owner authorization rules. Mutating actions require a clear confirmation round trip and must produce normal audit entries.

## Remote support

| Method and path | Purpose |
|---|---|
| `POST /remote-support/sessions` | Owner creates a short-lived session for one registered device; server returns random one-time pairing data |
| `GET /remote-support/sessions/{id}` | Only Owner and the paired Member may inspect session state |
| `POST /remote-support/sessions/{id}/approve` | Paired Member explicitly approves on the Android device |
| `POST /remote-support/sessions/{id}/signal` | Authenticated participants exchange bounded WebRTC offer/answer/ICE messages only after approval |
| `POST /remote-support/sessions/{id}/terminate` | Either participant terminates; Owner termination immediately invalidates signaling |

Pair codes expire, are one-use and rate-limited. Android must show the user a foreground approval prompt before MediaProjection starts. Screen capture and input control must use explicit OS/user permission; no hidden or unattended control. Audit create, approve, start, terminate and failure events.

## System endpoints

- `GET /public-key` — returns public RSA verification key only: `{ algorithm:"RSA-SHA256", key_format:"X.509", key_size:3072, public_key_pem, key_id }`. Never return a private key.
- `GET /health` — liveness/readiness only; no tokens, secrets, environment values, internal resource IDs or user records.

## Error semantics

| HTTP | Meaning |
|---|---|
| 400 | Invalid input, schema or recipe |
| 401 | Missing, invalid, expired or revoked Firebase ID token |
| 403 | User inactive, wrong role, device ownership failure or resource not authorized |
| 404 | Resource not found or not visible to caller |
| 409 | Concurrent version or state conflict |
| 413 | Request too large |
| 429 | Rate limit exceeded; return `Retry-After` |
| 500/503 | Server/provider failure; return opaque request ID, not internal exception or secret |

The backend, browser and Android clients must use these exact paths, request field names and envelopes. Version breaking changes explicitly and update contract tests before clients.


## Additional Admin-console read/write routes

These routes are used by the current Admin website client and must be implemented by Cloud Run so the whole console can move off Supabase without losing existing screens.

| Method and path | Role | Purpose / response |
|---|---|---|
| `GET /tool-versions?tool_id=&limit=&cursor=` | Owner | Return immutable versions with `id, toolId, version, signature, signatureAlgorithm, keyId, publishedAt, changelog`; bounded pagination |
| `GET /recipe-tests?limit=&cursor=` | Owner | Return static validation history with `id, recipeHash, validationStatus, simulatedStepCount, testedAt, issues` |
| `POST /recipe-tests` | Owner | Validate and append test history; body contains the recipe and validation metadata, never a claim of real-device test |
| `GET /tool-assignments?tool_id=` | Owner | Return assignment rows with `toolId, memberId`; never expose other users to Members |
| `DELETE /tools/{id}` | Owner | Delete a DRAFT-only tool with no published immutable versions; reject published/testing/archived versions and audit the deletion |

The Admin pages use the same API client as the Android contract. The current UI expects camelCase response fields for Owner data: `displayName`, `createdAt`, `deviceLabel`, `userId`, `appVersion`, `androidVersion`, `androidApi`, `lastSeenAt`, `revokedAt`, `updatedAt`, `draftRecipe`, `toolId`, `memberId`, `signatureAlgorithm`, `publishedAt`, `recipeHash`, `validationStatus`, `simulatedStepCount`, `testedAt`, and `totalRuns` / `activeMembersCount` / `publishedTools` analytics fields. Run-result fields remain `run_id`, `user_id`, `device_id`, `tool_id`, `tool_version`, `started_at`, `step_results`, `error_code` and `app_version` to match the Android outbox DTO.

The following aggregates are returned by `GET /analytics/dashboard`: `totalRuns`, `success`, `partial`, `failed`, `cancelled`, `activeMembersCount`, `publishedTools`, `mostUsedTools[{toolId,name,runs}]`, and `commonFailures[{errorCode,occurrences}]`. Metrics must be computed over persistent accepted run records, not from the latest UI page.

## Admin website authentication cookies

- `POST /api/auth/request-otp` and `POST /api/auth/verify-otp` are same-origin Next.js routes that call the Cloud Run OTP endpoints.
- After OTP verify, the Admin server exchanges the Firebase custom token with Firebase Identity Toolkit, then calls `POST /auth/verify` using the resulting Firebase ID token.
- Only the exact active Owner may receive the Admin browser session. A Member is rejected before cookies are written.
- Firebase ID token, refresh token and expiry are stored only in HttpOnly, Secure (production), SameSite=Lax cookies. The browser JavaScript never receives either token.
- The Next.js proxy refreshes the Firebase session before expiry. Server pages and every Server Action revalidate Owner through Cloud Run; the UI alone is never an access boundary.
- `FIREBASE_WEB_API_KEY` is used only for Google's public Identity Toolkit/refresh endpoints; it is not a service account or Admin credential. It must not be placed in a browser-visible `NEXT_PUBLIC_*` variable for this server-side flow.
