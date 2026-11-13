# Agent 4 handoff — Mahi Repair Admin Panel and remote support

## Branch and implementation baseline

- Repository: `mahimahfud-ui/Mahi-Repair`
- Working branch: `ai/mahi-repair-complete-workflow`
- Agent 3 continues the existing branch; changes are a fast-forward commit and do not merge into `main`.
- Admin Panel source: `admin-panel/`
- Render preview is configured to follow the same branch and use `/api/health`.

## Delivered by Agent 3

- Owner email link requests now run through a Next.js Server Action and disable automatic account creation.
- Auth callback exchanges the PKCE code, calls Supabase Auth `getUser()`, then requires `profiles.role = owner` and `profiles.status = active`. Non-Owner or inactive accounts are signed out and rejected.
- Supabase SSR cookies are HTTP-only, SameSite=Lax, Secure in production, and use an eight-hour browser cookie lifetime.
- Local Supabase Auth config sets a 12-hour session timebox and eight-hour inactivity timeout. Hosted Auth session settings still need to be set and verified in the Supabase Dashboard.
- Added owner-verified `GET /api/health` readiness response and owner-authenticated `GET /api/ops` snapshot.
- Added 30-second operational polling with exponential backoff up to five minutes. The snapshot uses real profiles, devices, repair-run and audit-log counts; it does not invent support-session or online state.
- Invitation history now displays existing `member.invited` audit records. The UI explicitly distinguishes invite-request acceptance by Supabase Auth from email delivery or member acceptance.
- Device inventory exposes only fields actually sent by Android: server device ID, label, app version, Android version/API, last check-in and revocation state. It also shows the last version stored by the real `device_tool_syncs` catalog endpoint. Its `synced_at` value is the row's first-recorded timestamp because the current Edge Function does not refresh it on upsert; this is historical sync telemetry, not proof of current access or hardware compatibility.
- Repair-run history and persistent administrative audit history are visible to Owners.
- Added the Owner App Update Manager at `/updates`: signed immutable manifest publication, private ZIP/APK upload, server-side SHA-256 calculation and release history. The form uses the current Android `applicationId`, binds APK `versionCode` to the selected app version code, and allows explicit content-package versions. Agent2 added native APK manifest/signature verification and system installer hand-off, with fresh manifest checks on app launch/resume and private update broadcasts.
- Added polling-delay unit tests, `/api/ops` route-level authorization/response tests using a mocked Supabase client, and updated Render preview branch/health-check configuration.

## Existing actions retained

- Owner-only member invitation and account suspension/revocation.
- Device revoke/restore.
- Repair-tool draft create/edit, static recipe validation, publish/sign, re-publish, disable/re-enable, archive, unpublish, assign, duplicate and delete draft.
- Server-verified Owner checks and existing allowlisted Edge Function mutations.
- Repair execution telemetry and audit review.

## Backend gaps that are intentionally not represented as working controls

Current Agent 1 APIs do not provide invitation email-delivery status, invitation acceptance timestamps, invitation expiry, resend or per-invitation revocation. They provide invitation creation and a `member.invited` audit record; member account status is separate and is not proof of invite acceptance.

The device registration contract does not contain manufacturer/model, pairing state, battery, storage or detailed hardware capability fields. Do not infer those from the device label or last-seen timestamp.

The current backend and Android app do not contain support-session state, consent workflow, WebRTC signaling, STUN/TURN credentials or Android MediaProjection screen capture. There is no real remote video to display. See `REMOTE_SUPPORT_GAP_ANALYSIS.md` before implementing this as a new cross-platform feature.

The signed app-update implementation on this integration branch includes native APK and data/config client paths. Android checks for a signed release after an authenticated session starts, on app resume and on the private `app_update_published` event. It verifies the exact RSA-signed manifest bytes; native APK assets are checked for size, SHA-256, package ID, increasing versionCode and signer-certificate continuity before Android's system installer opens for user approval. For data updates, `AppUpdateRepository` fetches each package through an authenticated short-lived asset URL, verifies the outer digest/size and calls `AppUpdateContentInstaller`; the extractor accepts only declared JSON/image entries, validates entry size and SHA-256, rejects traversal/duplicate/undeclared/unsupported entries and stages all content before atomically switching the active pointer. The previous data release is retained and the local rollback method is implemented. The rollback button is wired in the separate audit/fix branch, pending CI/review. The update path downloads each changed ZIP in full; binary delta patching is not implemented. Beta selection and per-device installation telemetry are also outstanding. None of these source paths is production-accepted until the branch is reconciled with current `main`, the production signer/backend are verified, and physical-device update/rollback tests pass. See `APP_LIVE_UPDATES.md`.

## Required Agent 4 follow-up

1. Obtain/define Agent 1's invitation read/resend/revoke/status APIs and their email provider delivery webhooks; persist exact status and expiry rather than inferring it.
2. Extend Android device registration with explicit manufacturer/model, pairing, battery, storage and capability fields only when the app can collect and securely submit them.
3. Build the consent-first WebRTC lifecycle end-to-end across Supabase Edge Functions/realtime, Android MediaProjection foreground service and browser peer; add database/RLS/audit and physical-device tests.
4. Configure hosted Supabase Auth timebox and inactivity timeout; verify with real owner and member accounts.
5. Connect the Render Blueprint to the intended preview branch, deploy and verify `/api/health`, owner login, protected routes and logout on the actual service.
6. Run current-head Admin Panel, Android and Supabase Edge Function CI. Perform live provider tests with real Resend/Brevo and Supabase secrets; CI does not prove provider delivery or a deployed Edge Function.
7. Capture screenshots only from the real deployed Owner console and actual approved Android stream once available.

## Safety boundary

Do not add fake online/offline, pairing, battery, storage, session or video values. Keep WebRTC and screen-control entry points hidden until both consent and real media paths work. Do not modify recipe-signing keys, Edge Function service credentials, or protected production deployment secrets in the Admin Panel.
