# Mahi Repair — Admin Feature Map & Architecture

This document describes the architectural features, data models, business rules, and security controls extracted from the Mahi Repair Admin source and ported to the Google Cloud / Firebase staging backend.

---

## 1. Authentication & Role Boundaries

### Account Roles
- **Owner (`role = 'owner'`)**:
  - Full administrative access to the catalog, members, devices, publication, and analytics.
  - Can assign tools, publish signed versions, suspend/revoke members, and revoke devices.
  - Requires **both** authenticated Owner credentials AND the 4–8 digit Admin Unlock PIN (`6993` initial) to perform sensitive modifications.
- **Member (`role = 'member'`)**:
  - End-user Android diagnostic client.
  - Strictly limited to reading their own profile, registering/updating their own device, retrieving signed tool releases explicitly assigned to them or published for `all_active_members`, and posting repair execution telemetry.
  - Cannot access administrative functions or bypass tool assignment restrictions.

### Account Statuses
- `active`: Standard operating status.
- `suspended`: Temporary hold; token rejected with `403 ACCOUNT_SUSPENDED`.
- `revoked`: Permanent revocation; device access and tokens immediately terminated.

---

## 2. Core Data Models

| Collection / Entity | Schema & Key Fields | Purpose & Invariants |
|---|---|---|
| `profiles` | `id` (UUID), `role` (`owner`/`member`), `status` (`active`/`suspended`/`revoked`), `email`, `displayName`, timestamps | Authoritative user identity. Role changes can only be performed by trusted server code. |
| `devices` | `id` (server-issued UUID), `userId`, `deviceLabel`, `appVersion`, `androidVersion`, `androidApi`, `registeredAt`, `lastSeenAt`, `revokedAt` | Registered Android device hardware profiles. A revoked device is immediately barred from recipe sync and telemetry. |
| `repair_tools` | `id`, `name`, `slug`, `category`, `description`, `icon`, `status` (`DRAFT`/`TESTING`/`PUBLISHED`/`DISABLED`/`ARCHIVED`), `audience` (`all_active_members`/`selected_members`), `version`, `draftRecipe`, compatibility metadata | Catalog definitions and working drafts. Archived tools cannot be edited or republished. |
| `repair_tool_versions` | `id`, `toolId`, `version`, `payload` (JSON), `canonicalPayload` (raw string), `signature` (RSA-3072 base64), `signatureAlgorithm` (`RSA-SHA256`), `changelog`, `publishedBy`, `publishedAt` | **Immutable, append-only releases.** Never updated or deleted. Signed with server private key. |
| `repair_tool_access` | `id`, `toolId`, `memberId`, `assignedBy`, `createdAt` | Explicit member assignments when tool `audience = 'selected_members'`. |
| `device_tool_syncs` | `deviceId`, `userId`, `toolId`, `version`, `syncedAt` | Tracks per-device synchronization state to ensure stale recipes are detected. |
| `repair_runs` | `id`, `userId`, `deviceId`, `toolId`, `toolVersion`, `startedAt`, `completedAt`, `result` (`SUCCESS`/`PARTIAL`/`FAILED`/`CANCELLED`), `stepResults`, `errorCode`, `appVersion` | Telemetry logs of client repair execution. Validated against registered devices. |
| `admin_audit_logs` | `id`, `actorId`, `action`, `targetType`, `targetId`, `details`, `createdAt` | **Append-only audit trail** recording all administrative mutations, PIN unlocks, tool state changes, and invitations. |
| `admin_settings` | `id`, `pinHash`, `pinSalt`, `iterations`, `updatedBy`, `updatedAt` | Salted PBKDF2 hashes of the Admin Panel unlock PIN. Never exposed to clients. |

---

## 3. Repair Tool Lifecycle & Publishing Workflow

```
[Draft Tool Created]
         │
         ▼
[Test Lab Simulation / Validation] ──── (Fails Schema / Actions) ──► Re-edit Draft
         │
         ▼ (Validates against 13 Allowable Actions)
[Publish Action Triggered by Owner]
         │
         ├─► Validate recipe against RepairRecipeZodSchema
         ├─► Sort object keys recursively (canonicalJson)
         ├─► Sign canonical UTF-8 bytes using RSA-3072 / SHA-256
         ├─► Commit immutable row to repair_tool_versions
         ├─► Bump version (v = v + 1) and set status = 'PUBLISHED'
         └─► Write entry to admin_audit_logs
```

---

## 4. Allowable Native Action Catalogue (Strict 13 Actions)

1. `CHECK_INTERNET`: Parameterless probe of active network interface.
2. `DNS_LOOKUP`: Resolves specific hostname (`host`).
3. `HTTPS_PROBE`: Verifies TLS handshake and HTTP response on `host`, optional `path`, `timeoutMs`.
4. `CHECK_APP_INSTALLED`: Validates presence of Android `packageName`.
5. `CHECK_STORAGE`: Asserts minimum available internal storage in megabytes.
6. `OPEN_APP`: Requests user confirmation and opens application package.
7. `OPEN_APP_DETAILS`: Requests user confirmation and navigates to Android App Info settings.
8. `OPEN_ANDROID_SETTINGS`: Opens settings subscreen (`wifi`, `internet`, `bluetooth`, `display`, `sound`, `app_notifications`, `date_time`, `storage`).
9. `SHOW_GUIDE`: Presents structured interactive instructions modal (`title`, `instructions[]`).
10. `ASK_USER_CONFIRMATION`: Halts execution until explicit user acknowledgement (`title`, `message`).
11. `REQUEST_OWN_APP_PERMISSION`: Requests runtime permission for own app (`POST_NOTIFICATIONS`, `CAMERA`, `ACCESS_FINE_LOCATION`, `READ_MEDIA_IMAGES`).
12. `VERIFY_INTERNET`: Post-repair assertion of internet connectivity.
13. `VERIFY_DNS`: Post-repair assertion of hostname resolution.

**Security Constraints**:
- Arbitrary shell commands, JavaScript execution, downloaded APK installation, reflection, and silent system changes are strictly prohibited.
- Confirmation is mandatory (`requiresConfirmation = true`) for `OPEN_APP`, `OPEN_APP_DETAILS`, `OPEN_ANDROID_SETTINGS`, `ASK_USER_CONFIRMATION`, and `REQUEST_OWN_APP_PERMISSION`.
