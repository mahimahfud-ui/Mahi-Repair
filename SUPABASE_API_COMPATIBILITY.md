# Mahi Repair — Supabase API & Backend Contract Compatibility Audit

**Date**: November 6, 2026  
**Supabase Reference**: `shwqsljicmhflnvjqjah` (`https://shwqsljicmhflnvjqjah.supabase.co`)  
**Backend Reference**: Branch `agent/supabase-backend-realtime` (`83a050413cff9f15a3fd0f881ff3e978287e533d`)  
**Admin Repository Reference**: Branch `integration/connect-shared-supabase` (`727e7e97dadb71b685d1b2ef803c4e49f99d5137`)  
**Android Client Reference**: Branch `agent/android-premium-ui` (`40f8f39a9bdacb8fd9d4d8b9a0e08e551d26dabe`)  

---

## 1. Comparative Architecture Overview

The Mahi Repair system is designed with a decoupled architecture:
- **Owner Admin Console**: A Next.js web application deployed on Render (`https://mahi-repair-admin.onrender.com`) interacting with Supabase as Owner (`role: owner`).
- **Android Member App**: A native Kotlin application interacting with Supabase as Member (`role: member`).
- **Shared Backend**: Supabase PostgreSQL database, GoTrue Authentication, and 15 Edge Functions under project `shwqsljicmhflnvjqjah`.

The Android client does **not** communicate with the Render web application. Both clients interact directly with Supabase via their respective authorized roles.

---

## 2. Endpoint Contract Matrix

| Endpoint | Method | Expected Request Body | Response Format | Status / Compatibility |
| :--- | :---: | :--- | :--- | :--- |
| `POST /register-device` | POST | `{ device_id?: string, device_label?: string, app_version: string, android_version: string, android_api: number }` | `{ data: { device_id: string, registered: true } }` | **100% Compatible** with both Admin and Backend branches. |
| `POST /get-my-repair-tools` | POST | `{ device_id: string }` | `{ data: [ { tool_id: string, version: number, signed_payload: string, signature: string, signature_algorithm: string, published_at?: string } ] }` | **100% Compatible**. Both return identical data array of signed payloads. |
| `POST /get-signed-repair-tool` | POST | `{ tool_id: string, device_id: string }` | `{ signed_payload: string, signature: string, signature_algorithm: string, version: number }` | **100% Compatible**. Matches Android `SignedRecipeResponse` DTO. |
| `POST /submit-repair-run` | POST | `{ run_id: string (UUID), device_id: string, tool_id: string, tool_version: number, started_at: string, completed_at?: string, result: string, step_results: Array, error_code?: string, app_version: string }` | `{ data: { run_id: string, accepted: true, duplicate: boolean } }` | **100% Compatible**. Both implementations return `{ data: { run_id, accepted } }`. |

---

## 3. Deep Dive: `submit-repair-run` & Idempotency Contract

### Admin Repo Implementation (`admin-repo/supabase/functions/submit-repair-run`)
- Checks for an existing run with `value.run_id`.
- If found and identical, returns HTTP 200 with `{ data: { run_id: existing.id, accepted: true, duplicate: true } }`.
- If found but altered, throws HTTP 409 conflict.
- Inserts new record and returns HTTP 201 with `{ data: { run_id: data.id, accepted: true, duplicate: false } }`.

### Agent 2 Backend Branch (`backend-repo/admin-panel/supabase/functions/submit-repair-run`)
- Delegates transactional insert and fingerprint verification to SQL RPC `submit_repair_run_idempotent`.
- Resolves duplicate vs initial insert and returns HTTP 200/201 `{ data: { run_id: value.run_id, accepted: true, duplicate: outcome.duplicate } }`.

### Android Client Compatibility
In `com.example.data.remote.dto.Dto.kt`:
```kotlin
@JsonClass(generateAdapter = true)
data class SubmitRunData(
    @Json(name = "run_id") val runId: String,
    val accepted: Boolean
)

@JsonClass(generateAdapter = true)
data class SubmitRunResponse(
    val data: SubmitRunData
)
```
In `ReportUploadWorker.kt`:
- The client persists the exact report payload in the Room outbox with its client-generated `runId` UUID.
- Retries use the exact same `run_id` and payload bytes.
- The outbox item is pruned only when `response.isSuccessful && response.body()?.data?.accepted == true && response.body()?.data?.runId == request.runId`.
- **Verdict**: Both Edge Function variants return `{ data: { run_id, accepted, ... } }`. The Android client is completely compatible with both!

---

## 4. Private Realtime Topic & Event Policies

- **Channel Topic**: `mahi-repair:member:{userId}:device:{deviceId}`
- **Channel Security**: Enforced via PostgreSQL Realtime Broadcast authorization policy in migration `20261106120000_private_realtime_catalog_broadcast.sql`.
- **Allowed Events**:
  1. `catalog_changed`: Emitted when Owner publishes, disables, updates, or changes assignments of a tool. Android client triggers background sync via `SyncManager.triggerSync()`.
  2. `member_access_changed`: Emitted when Owner suspends or modifies member status. Android client re-verifies session.
  3. `device_revoked`: Emitted when Owner revokes a device. Android client immediately invalidates cached device state and halts execution.
- **Payload Guarantee**: Realtime payloads contain opaque notifications only. No raw recipes, signatures, or tokens are ever sent over Realtime websockets.

---

## 5. Cryptographic Signing Contract

- **Algorithm**: `RSASSA-PKCS1-v1_5` with `SHA-256`.
- **Key Length**: RSA-3072 strictly enforced on both sides:
  - Backend `sign.ts`:
    ```typescript
    const modulusLength = (key.algorithm as RsaHashedKeyAlgorithm).modulusLength;
    if (modulusLength !== 3072) throw new Error('The repair signing key must be RSA-3072.');
    ```
  - Android `RsaSignatureVerifier.kt`:
    ```kotlin
    require((publicKey as? RSAKey)?.modulus?.bitLength() == 3072) {
        "The repair signing key must be RSA-3072"
    }
    ```
- **Signing Payload**: Canonical UTF-8 JSON bytes (RFC 8785 canonical format).
- **Detached Signature**: Base64 encoded.
