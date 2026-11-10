# Google AI Studio Handoff — Mahi Repair

**Purpose:** Prepare the existing Android app and Owner Admin website for a Google-managed production backend. Google AI Studio is responsible for implementing and provisioning the Firebase/Google Cloud backend. This document records the actual repository state, fixed product constraints, and the required completion gates.

## 1. Source of truth and scope

- Primary repository: `mahimahfud-ui/Mahi-Repair`.
- Current default branch at handoff: `main`; use a dedicated feature branch and open a PR back to `main`.
- Android application: `app/` (Kotlin, Jetpack Compose).
- Canonical Admin website inside this monorepo: `admin-panel/` (Next.js App Router, TypeScript, Tailwind).
- Existing shared recipe contract: `admin-panel/schemas/repair-recipe-v1.schema.json`.
- Existing recipe-signature verifier and tests: `app/src/main/java/com/example/security/` and `app/src/test/`.
- `Mahi-Repair-Admin` is a second repository with an overlapping Supabase-era Admin implementation. It may be read for UI/features that are missing from `admin-panel/`, but do not create a second production Admin backend, deploy two competing consoles, or split the data model. Port only useful UI/features into the canonical `admin-panel/` when necessary.
- The root `src/` tree contains an older React/Express staging implementation. Do not use its file-backed demo database as production storage.

The intended product is **one Mahi Repair system with two clients**: an Android APK and a responsive Owner Admin website. They must use the same Firebase identity, roles, persistent data, API contracts, audit trail, and tool catalogue. Do not try to package the Admin website inside the APK or implement a second backend for either client.

## 2. Fixed identity and authorization rules

These identities are intentionally different and must remain different:

| Account | Required role | Rule |
|---|---|---|
| `mahinmhfd@gmail.com` | **Owner** | The only currently authorized Owner identity |
| `mahimahfud@gmail.com` | **Member** | Keep as Member; never promote, bootstrap, or grant Owner claims |

Authorization requirements:

1. The server must derive the role from a server-controlled profile/claim. Never trust a role, email, boolean, or user ID supplied by the browser or Android request body.
2. Only the Owner identity above may be bootstrapped as Owner. The bootstrap must be an explicit, audited, one-time server operation; never provide public Owner registration.
3. A Member must be unable to open the Admin website, call any Owner API, enumerate other users/devices, publish or assign tools, change account roles, or read audit logs.
4. Owner access requires both the authenticated identity and an active server-side Owner profile. Suspended/revoked accounts must fail closed.
5. Invite flows create Members only. The Admin UI must not offer a role selector that can create another Owner.
6. Verify the email and profile mapping during migration. Firebase user UIDs may differ from Supabase UIDs; do not assume UID equality. Require verified re-authentication and preserve the two roles exactly.
7. Do not create a demo Owner session, local PIN bypass, hard-coded password/PIN, client-side role switch, or fake local authentication flow.

## 3. Firebase project and database — verify before provisioning

The repository already contains Firebase project configuration:

- Firebase project ID: `manifest-hawk-76tvw`.
- The existing health-route configuration names Firestore database ID `ai-studio-mahirepair-a294799a-c294-4076-998a-6efc3ff9c0b7`.
- Existing Firestore security rules are in `firestore.rules`.
- Entity/model blueprint is in `firebase-blueprint.json`.

**Do not create another Firebase project or Firestore database by default.** First open the linked Firebase project and verify that the named database actually exists, is accessible, and is in the intended location. Firestore location is a permanent infrastructure decision. If AI Studio cannot attach to this existing database, or the ID/location cannot be confirmed, stop and report the exact blocker for the Owner. Do not silently create a second database, change location, enable billing, or enable paid services.

The web Firebase config contains a client API key. Treat it as public client configuration, not as a server secret; do not print or copy it into documentation. No service-account JSON, private key, webhook secret, RSA signing key, or provider key may be committed.

## 4. Current implementation inventory

### Android

The current Android app is not yet migrated to Firebase APIs. These source files still use Supabase and must be migrated as one coherent flow:

- `app/src/main/java/com/example/data/remote/SupabaseClient.kt` — email OTP, session refresh/sign-out, device registration, tool catalogue, signed-recipe retrieval, run submission.
- `app/src/main/java/com/example/data/remote/SupabaseOwnerAdminApiClient.kt` — Owner dashboard and privileged tool/member/device operations.
- `app/src/main/java/com/example/sync/SupabaseRealtimeClient.kt` — authenticated private realtime subscriptions.
- `app/src/main/java/com/example/data/repository/AuthRepository.kt` — authentication/session orchestration currently backed by Supabase.
- `app/src/main/java/com/example/data/remote/AdminApiClient.kt` — legacy `MAHI_API_BASE_URL` endpoints; do not point this at the Admin website or treat the legacy API as production storage.
- `app/src/main/java/com/example/ui/screens/AdminScreen.kt` — the in-app Owner console currently calls Supabase Owner APIs.
- `app/src/main/java/com/example/security/` — existing recipe verification/security boundary; preserve and test it during migration.

Replace the Supabase implementations with Firebase Auth + the authenticated Cloud Run API + Firestore listeners/FCM as appropriate. Keep repositories and ViewModels stable where possible so the UI does not needlessly regress. Remove Supabase dependencies only after all Android flows have moved and the new build/tests pass.

The stable Android application ID is **`com.aistudio.mahirepair.qrzntk`**. Preserve it. Preserve the existing upload-signing identity and update compatibility; do not generate a new upload key, change package ID, or commit keystore files. A debug APK is not evidence that an existing installation can be upgraded.

### Owner Admin website

The canonical Admin website is `admin-panel/`. It already contains dashboard, tools, tool editor, test lab, publishing, versions, members/devices, repair logs, analytics and security areas.

Current Supabase-coupled areas include:

- `admin-panel/src/lib/auth/require-owner.ts`
- `admin-panel/src/lib/supabase/browser.ts`
- `admin-panel/src/lib/supabase/server.ts`
- `admin-panel/src/app/actions.ts`
- `admin-panel/src/app/auth/callback/route.ts`
- `admin-panel/src/app/login/page.tsx`

Migrate these to Firebase Authentication and server-verified Cloud Run APIs. Keep all Owner checks on the server. Browser code must never receive service-account credentials or privileged API secrets. Preserve the existing responsive Admin UI and workflows rather than replacing them with an unrelated scaffold.

### Existing backend and unsafe legacy paths

- `src/backend/` contains the legacy Express server and a file-backed `MemoryStagingDb`/demo-data implementation. It is not a durable database and must not become a Cloud Run production data store.
- `src/backend/routes/public.ts` exposes legacy in-memory data-pack catalog/status/publish routes. The publish route is not a secure, persistent production publishing workflow. Disable/remove these routes from production or replace them with Owner-authenticated, validated, audited Cloud Run endpoints.
- The legacy Gemini endpoint and remote-support routes are not proof of production AI or remote support. Do not claim they are live until the server transport, auth, consent, audit, and real-device tests pass.

## 5. Google-managed target architecture

Use one shared backend for both frontends:

- **Firebase Authentication** for real email-based sign-in and session management.
- **Firebase App Check** for supported client attestation; enforce it on protected client-accessible services after debug/test configuration is separated from production.
- **Cloud Firestore** for durable profiles, devices, tool drafts, immutable tool versions, member assignments, per-device sync state, repair runs, audit events and remote-support session metadata.
- **Cloud Run API** for all privileged operations, validation, role enforcement, device enrollment/revocation, recipe publication/signing, reports, Gemini requests and remote-support signaling.
- **Secret Manager** for server-only secrets (Gemini credentials, RSA signing private key, any provider secrets).
- **Cloud Logging / Error Reporting / Monitoring** for structured audit/operational logs and alerts.
- **Firebase Cloud Messaging and/or authenticated Firestore listeners** for update notifications and live state, with server-side authorization.
- **Firebase AI Logic + App Check** for approved on-device Gemini use only where appropriate. For Admin website or server-side tasks, call Gemini from Cloud Run using Secret Manager. Never embed a Gemini API key in the APK or browser bundle.

A Firestore listener may notify the APK that a published tool version changed; the APK must then fetch the signed immutable recipe, verify the signature, check compatibility, show a clear update prompt, and only execute after user confirmation where required. A live notification is not itself authorization to execute a tool.

## 6. Shared data model

Use the existing `firebase-blueprint.json` as a starting model and reconcile it with the actual Android and Admin DTOs. Keep stable IDs and explicit schema versions. At minimum, support:

- `profiles`: Auth UID, normalized verified email, role, status, display name, timestamps.
- `devices`: server-issued device ID, owner/member UID, app/device build metadata, consented hardware profile, field-availability map, last seen, revocation status.
- `repair_tools`: metadata, draft recipe, status, target/audience, compatibility rules, current version.
- `repair_tool_versions`: immutable signed payload, schema version, signature algorithm, key ID, version, changelog and publisher.
- `repair_tool_access`: per-member grants for selected-member tools.
- `device_tool_syncs`: last delivered/installed version and sync state per device/tool.
- `repair_runs`: bounded result, step outcomes, timestamps, app/tool versions and idempotency key.
- `admin_audit_logs`: append-only record of Owner mutations.
- `recipe_test_history`: Owner-only validation and dry-run history.
- `remote_support_sessions`: expiring pairing/session state, participants, consent, status and termination metadata.

Never trust a client-supplied UID, role, device owner, tool audience, publisher ID, signature, installed version, or audit actor. Derive or verify these on the server. Restrict telemetry to documented, consented fields. Do not collect contacts, messages, photos, files, passwords, unrelated identifiers or contents of personal data.

## 7. API contract and recipe security

The repository's `openapi.json` contains a **staging-era API outline**, not a verified live Google backend. Reconcile it with actual Cloud Run handlers and keep one versioned contract for both clients.

Required logical operations:

- Auth/session validation and current profile retrieval.
- Owner dashboard, member invitations/status, device listing/revocation.
- Create/update/test/delete draft tool, publish immutable signed version, disable/archive tool, assign/unassign tool.
- Device registration/update and current device profile submission.
- Member-authorized catalogue retrieval and one-tool signed recipe retrieval.
- Idempotent repair-run submission and Owner-only run/analytics/audit retrieval.
- Remote-support pairing, consent, signaling authorization, session status and termination.
- Health/readiness endpoints that expose no secrets, tokens, user details or internal configuration.

Preserve the existing recipe contract in `admin-panel/schemas/repair-recipe-v1.schema.json` and Kotlin verification boundary. The backend must validate every action and parameter against the schema before publication. Canonicalize the exact payload deterministically, sign it server-side with RSA-SHA256, and return the exact signed bytes/string plus signature, algorithm and key ID. The private key must never leave Secret Manager/Cloud Run. Android must verify the signature **before parsing or executing**, then validate schema version, action allowlist, parameters, app/device compatibility and expiry/revocation. Reject a modified payload, unknown action, unsupported schema, invalid signature, unknown key ID or revoked access. Never download or execute arbitrary scripts/shell commands.

## 8. Migration and cutover order

1. Inspect the existing Firebase project/database and current repo before generating infrastructure. Confirm database ID/location; do not create duplicates.
2. Implement real Firebase Auth and server-managed Owner/Member profiles. Verify both known accounts and their roles before opening the Admin console.
3. Implement Firestore schema, strict default-deny rules, indexes and emulator rule tests. Client writes to privileged collections must remain denied.
4. Implement Cloud Run endpoints with verified Firebase ID tokens, App Check verification where supported, Owner/Member role enforcement, input validation, rate limits, idempotency, audit logs and structured errors.
5. Implement server-only RSA signing and key rotation/key-ID support. Verify Kotlin compatibility with known-good and tampered fixtures.
6. Migrate the Admin website to Firebase Auth/Cloud Run while preserving the UI. Confirm a Member cannot enter the website or call Owner endpoints directly.
7. Migrate Android authentication, enrollment, tools, signatures, Owner console, reports and realtime/update flow. Keep the app ID and upload signing identity stable.
8. Migrate only verified records. Map users by verified email and explicit role; do not assume Supabase/Firebase UIDs match. Reconcile counts and record a migration report.
9. Run automated CI, emulator/rules tests, API integration tests and physical Android tests. Test Owner and Member separately.
10. Cut over traffic only after the acceptance checklist passes. Keep Supabase and Render available as rollback/reference until Google production is proven. Do not delete or disable the old system as part of initial implementation.

## 9. Build, release and deployment constraints

- Keep the three existing GitHub checks green: Android CI, Admin Panel CI and Backend CI.
- Use read-only CI permissions unless a narrowly scoped workflow demonstrably needs more. CI must not commit, push, modify branches or upload secrets.
- Keep secret values in Secret Manager or protected GitHub/hosting secrets. Commit only sanitized examples and public configuration.
- Build and test both targets. Do not treat Next.js build success as proof the Admin API is secure or Android build success as proof Firebase login works.
- Do not enable billing, paid APIs, production rollout, destructive migration, account role changes, DNS cutover or old-service shutdown without explicit Owner authorization.
- Provide a deployment summary with exact project/database/region, services, indexes/rules, environment variable *names* (never values), test evidence, remaining blockers and rollback procedure.

## 10. Definition of done

This task is not complete merely because AI Studio generates code or displays a preview. It is complete only when the same Firebase backend serves the responsive Admin website and Android app; Owner and Member permissions are proven with real accounts; tool creation/testing/publishing produces immutable signed versions; the Android device receives an update, verifies it locally, executes only allowlisted actions and submits an idempotent report; device status and logs appear live in Admin; and all security, emulator, CI, real-device, migration and rollback checks are evidenced.

If a required Firebase resource is unavailable or cannot be safely reused, stop and report the blocker instead of silently creating a duplicate or weakening security.
