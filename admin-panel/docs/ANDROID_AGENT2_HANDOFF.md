# Android Agent 2 handoff — Mahi Repair

Branch: `ai/mahi-repair-complete-workflow`  
Backend base: `security-hardening/run-repair`

## Android changes in this handoff

- The app accepts only `mahirepair://invite?token_hash=<opaque-token-hash>` invitation links. It verifies the token through Supabase Auth `/auth/v1/verify` with `type=invite`, rejects malformed/expired/reused links, and persists returned access/refresh tokens using the existing Android Keystore-backed DataStore implementation.
- Manual invited-email OTP remains available as the recovery path when a link is invalid or expired. Supabase Auth metadata parsing tolerates the normal mixed metadata JSON shape (including provider arrays) rather than assuming every app-metadata value is a string.
- The existing server-issued device registration and signed tool catalogue remain the authorization gate. The app heartbeats its existing device UUID once per app process (including app start), updates the documented version/build metadata, and does not mint a replacement when the backend rejects a revoked UUID. Catalog and signed-recipe Edge Functions independently re-check server-side device ownership/revocation. The view model exposes whether a server-issued device ID is saved locally and provides a sign-out action.
- The app displays real local diagnostics (manufacturer/model, Android/API/build, supported CPU ABIs, memory, storage, battery where reported, display, network validation, advertised hardware features, and app version). It does not read IMEI, serial, MAC address, location, installed-app inventory, or personal content. These diagnostics are not uploaded; `register-device` still receives only the documented minimal device label/app version/Android version/API fields.
- Unit tests cover invitation URI acceptance and malformed/foreign URI rejection. Android CI is configured to run Gradle unit tests, build a debug APK, run Compose UI tests on an emulator, and upload the APK and reports.

## Supabase Auth redirect configuration

The current `admin-invite-member` Edge Function only accepts redirect URLs whose origin matches `ADMIN_ORIGIN`; it rejects `mahirepair://invite` because it is not the configured HTTPS origin. Therefore the new Android URI handler is not yet reachable through the existing owner invitation flow. Agent 1 must add an explicit, allowlisted Android redirect path (or a hosted HTTPS handoff page that securely transfers the already-verified Auth session into the app) before invitation deep links can be treated as end-to-end complete. Add any chosen redirect to the Supabase Auth Redirect URLs allowlist. Do not weaken the origin check to accept arbitrary redirects. Until that server-side handoff exists, the existing invited-email six-digit OTP path remains the supported activation/sign-in route.

## Confirmed backend contract gaps — not simulated in Android

The checked-in `admin-panel/docs/ANDROID_API_CONTRACT.md`, Edge Function tree and Realtime migration define member OTP/device registration/tool sync/signed recipes/run telemetry. They do **not** define:

1. A pairing-token create/redeem/revoke endpoint, QR payload format, one-time nonce semantics, expiry, or owner-to-device binding.
2. Remote-support session create/approve/deny/end endpoints, a durable session state machine, consent receipt, replay protection, or session audit-event schema.
3. Authenticated WebRTC offer/answer/ICE signaling for both owner and member identities, or expiring STUN/TURN credentials.

The current private Realtime policy grants authenticated members **SELECT** access to opaque Broadcast events on their own device topic. Server-side database triggers send only `catalog_changed`, `member_access_changed`, and `device_revoked`. There is no client broadcast INSERT policy or owner/member signaling channel. This cannot securely carry WebRTC SDP/ICE messages, and the member-only topic must not be repurposed as an owner signaling channel.

Consequently no Android QR pairing or live WebRTC session is claimed as complete. There is no silent capture path. Screen capture must remain behind a foreground, per-session MediaProjection consent prompt.

## Required Agent 1/backend contract before remote support can be enabled

- Define signed-in owner and active-member authorization rules for pairing, session creation, approval, signaling, revocation and ending.
- Provide short-lived, single-use pairing tokens bound to owner, target account/device, purpose and expiry; define QR JSON schema and revoke/redeem semantics.
- Create a server-owned remote session row with device/user/owner IDs, created/expiry timestamps, approved/ended/revoked state, explicit consent evidence, and append-only audit events.
- Provide authenticated bidirectional signaling (offer, answer, ICE candidate, ICE completion, hangup) with per-session authorization, strict payload limits, replay protection and server-side expiry. Do not expose arbitrary Realtime writes to authenticated clients.
- Provide short-lived TURN credentials and an approved STUN/TURN list. Do not store shared TURN secrets in the APK.
- Broadcast a minimal incoming-support notification to the correct active device, and revoke signaling immediately when a session expires, is ended, consent is withdrawn, or either account/device is revoked.

## Build and device validation

The current execution environment could not resolve `github.com` for a local clone, so Gradle could not be run in this session. Pushing the branch triggers the repository `android-ci.yml` workflow, which is the authoritative build/unit/UI-test and debug-APK check. A physical Android device is not attached to this environment; MediaProjection consent, vendor-specific hardware reporting, background restrictions, and real network behaviour still require device QA.