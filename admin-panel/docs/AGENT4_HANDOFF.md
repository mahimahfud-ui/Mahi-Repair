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
- Device inventory exposes only fields actually sent by Android: server device ID, label, app version, Android version/API, last check-in and revocation state.
- Repair-run history and persistent administrative audit history are visible to Owners.
- Added polling-delay unit tests and updated Render preview branch/health-check configuration.

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
