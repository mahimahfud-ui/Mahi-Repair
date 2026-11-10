# Google AI Studio — Remaining Work Only

The repository's Admin website transport, Admin login/session flow, route contract and Supabase-to-Cloud-Run server-page/action migration are already prepared in this branch. Do **not** rebuild those parts. Start from the latest commit of `prep/firebase-cloudrun-client-contract` after its PR is merged to `main`.

## Already implemented in the repository

- Shared Cloud Run REST client for the Admin website: `admin-panel/src/lib/api/cloud-run.ts`.
- Firebase OTP challenge client, Firebase custom-token exchange and refresh-token handling: `admin-panel/src/lib/auth/firebase-rest.ts`.
- HttpOnly cookie session, refresh proxy, same-origin OTP routes and server-side Owner verification.
- Admin login UI changed from Supabase magic links to a 6-digit OTP flow.
- All active Admin website pages, data reads and mutations use Cloud Run API client; they no longer import Supabase client modules.
- Added API integration/contract tests and static checks against active Supabase imports.
- Full API route specification: `openapi.json` and `docs/CLOUD_RUN_API_CONTRACT.md`.
- Full identity, Firebase project reuse, security, recipe signing, migration and rollback constraints: `docs/GOOGLE_AI_STUDIO_HANDOFF.md`.
- Production acceptance gates: `docs/GOOGLE_BACKEND_CUTOVER_CHECKLIST.md`.

The old `admin-panel/src/lib/supabase/`, `admin-panel/supabase/` and Supabase packages are retained as migration reference/rollback material only. Do not wire the Admin runtime back to them. Do not delete the live Supabase project during this task.

## Only the work still requiring Google/Firebase infrastructure and Android integration

### 1. Verify existing Google resources before provisioning

- Open the existing Firebase project `manifest-hawk-76tvw`.
- Verify whether the already-referenced Firestore database `ai-studio-mahirepair-a294799a-c294-4076-998a-6efc3ff9c0b7` exists, is accessible and has the intended location. Firestore location is permanent.
- Reuse that project and database. Do not create another project/database, change location or enable billing without explicit Owner approval.
- Register the existing Android package ID `com.aistudio.mahirepair.qrzntk` and provide the correct Firebase web/app configuration. Keep downloaded config files free of service-account secrets.
- If AI Studio cannot use the existing database/project, stop and report the exact blocker instead of creating a duplicate.

### 2. Implement the production Cloud Run backend

Implement every operation defined in `openapi.json` and `docs/CLOUD_RUN_API_CONTRACT.md`, with persistent Firestore data, request validation, pagination, rate limits, structured errors, idempotency and server-side authorization.

Minimum endpoints:
- Firebase custom-token OTP request/verify for pre-provisioned accounts only.
- Firebase ID-token verification and authoritative profile lookup.
- Device registration, list/revoke, consented telemetry and field availability.
- Tool CRUD, schema validation, test-history persistence, assignment, disable/archive/delete-draft and immutable publication.
- Recipe/version listing and authorized signed recipe delivery.
- Idempotent repair-run submission and Owner-only reporting, analytics and audit history.
- Gemini Owner assistant using an explicit allowlist and confirmation for mutations.
- Expiring remote-support session creation, Member approval, authenticated WebRTC signaling and immediate termination.
- Public signing key and health endpoints.

### 3. Provision and secure Firestore

- Build profiles, devices, repair tools, immutable versions, Member access grants, sync state, test history, repair runs, audit logs and remote-support sessions.
- Server-side writes only for privileged collections.
- Default-deny rules, required indexes, rate limits and emulator tests for anonymous, Owner, Member, suspended, revoked and cross-user access.
- App Check for supported Android-facing endpoints, separate debug/production providers.
- Store Gemini credentials, Firebase Admin credentials and RSA-3072 private signing key only in Google Secret Manager.

### 4. Wire production OTP delivery and token lifecycle

- Implement OTP generation, hash-at-rest storage, challenge binding, one-time use, expiry, attempt limits and 60-second resend cooldown.
- Use a verified sender/provider and demonstrate actual email receipt.
- OTP verification must issue a short-lived Firebase custom token only for an existing active profile. The client then exchanges it with Firebase Auth.
- Never create or promote users during sign-in.
- Owner web session is already coded to deny every identity except the active `mahinmhfd@gmail.com` Owner profile.
- `mahimahfud@gmail.com` must remain Member; never promote or rewrite this account.

### 5. Complete Android Firebase integration

Android still uses Supabase in its current auth, Owner API and Realtime classes. Migrate those runtime connections to:
- Firebase Auth custom-token sign-in, ID token refresh, secure persistence and sign-out.
- Firebase App Check production provider.
- Cloud Run API calls with Firebase ID token, server-issued device ID and App Check.
- Firestore listeners and/or FCM update notifications.
- Signed recipe download, exact-byte RSA-SHA256 verification before parsing, compatibility checks, native action allowlist and user confirmation.
- Idempotent repair-run upload and session/device revocation handling.
- In-app Owner controls calling the same Cloud Run API; never grant Owner based on an Android UI flag or local PIN.

Preserve package ID `com.aistudio.mahirepair.qrzntk`, existing app data migration needs and upload-signing identity. Do not generate a new APK signing key.

### 6. Real integration, release and cutover

- Validate Admin website against deployed Cloud Run using the real active Owner account. A Member must be rejected before an Admin session cookie is written.
- Test a physical Android Member device: OTP, device registration, assigned tool sync, signed recipe verification, user-approved execution, report upload and Owner visibility.
- Test device/member revocation and retry/idempotency behavior.
- Test remote support on a real device with visible Member approval, Android MediaProjection permission, bounded WebRTC signaling, audit and immediate termination.
- Run Android CI, Admin Panel CI, Backend CI, Firestore emulator tests and API integration tests on the exact final commit.
- Keep Supabase and Render live for rollback until every acceptance item passes. Do not delete old infrastructure as part of initial cutover.
- Return a concise evidence report: tested commit SHA, project/database/location, deployed Cloud Run URL, service names, secret variable names only, test links, failed cases and rollback procedure.

## Stop conditions

Do not silently create another Firebase database/project, enable billing, change Owner/Member roles, expose secrets, replace Android application ID/signing identity, or turn off Supabase/Render. If any required existing resource cannot be reused, report the blocker for the Owner.
