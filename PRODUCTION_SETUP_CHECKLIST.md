# Mahi Repair — production setup and acceptance checklist

**Verified snapshot:** November 8, 2026 (UTC)  
**Production Supabase project:** `shwqsljicmhflnvjqjah`  
**Owner Admin:** [mahi-repair-admin.onrender.com](https://mahi-repair-admin.onrender.com)  
**Android repository:** `mahimahfud-ui/Mahi-Repair`  
**Admin repository:** `mahimahfud-ui/Mahi-Repair-Admin`

This document records verified infrastructure and remaining release gates. An ACTIVE function or successful CI build is not proof of end-to-end production acceptance.

## 1. Verified infrastructure

- Supabase project status: `ACTIVE_HEALTHY`; PostgreSQL 17.
- 9 migrations are recorded in production.
- 17 Edge Functions are `ACTIVE`: 16 application/API functions have `verify_jwt=true`; `send-email-hook` has `verify_jwt=false` and authenticates Supabase Auth requests with the Standard Webhooks signature.
- RLS is enabled on all 9 public application tables and on `realtime.messages`.
- Anonymous browser table grants are absent. Authenticated browser access is read-only; privileged database RPCs are service-role only.
- Supabase device profile/telemetry columns and the member-owned registration RPC are deployed.
- `register-device` v5 and Owner-only `get-admin-dashboard` v3 are deployed. The dashboard function was deployed after confirming the mobile Owner client called an endpoint missing from the live function inventory.
- `send-email-hook` v1 is deployed (Resend primary, Brevo fallback), but has not been connected in Supabase Auth.
- Admin source PR #4 is merged. Render Admin commit `098274ed7da6b368f828f6249a2784fbf2ea02ac` is live.
- Android PR #14 is merged. Member device telemetry and mobile Owner dashboard now use Supabase functions instead of the unconfigured legacy Mahi API.
- Android PR #15 is merged. A manual signed APK/AAB release workflow is available; it has not produced a production-signed artifact because the existing upload keystore secrets are not configured/verified.
- Existing Auth profiles remain one active Owner and one active Member. Their roles were not changed.

Current production counts: **0 devices, 0 repair tools, 0 published versions, 0 repair runs, 0 admin audit events**. No fake test devices, accounts, tools or telemetry rows were inserted.

## 2. Protected secret requirements

Keep all server secrets in Supabase Edge Function secrets or protected GitHub Actions secrets. Never put service-role keys, SMTP credentials, PINs, signing private keys or keystore passwords in source files, Android resources, public environment variables or PR content.

Required Supabase secret checks:

- `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` must be present in the Edge Function environment.
- `ADMIN_ORIGIN` is an optional override. If set, it must equal the canonical HTTPS origin `https://mahi-repair-admin.onrender.com` (no path, query or trailing slash). If unset, all deployed app functions use this fixed production origin as their default.
- `RECIPE_SIGNING_PRIVATE_KEY_PEM` must be a valid RSA-3072 PKCS#8 private key and must cryptographically match Android's configured `RECIPE_SIGNING_PUBLIC_KEY_PEM`.
- Auth email delivery requires `SEND_EMAIL_HOOK_SECRET` (generated in Supabase Auth Hooks), `RESEND_API_KEY`, `BREVO_API_KEY`, and a sender address configured as `AUTH_EMAIL_FROM`; both provider accounts must verify that sender. Optional: `AUTH_EMAIL_FROM_NAME` and `RESEND_EMAIL_FROM`.

The connected Supabase API does not reveal protected secret values. Auth hook/provider secrets, signing-key format, and public/private key match therefore remain unverified. The app-function CORS/invite origin no longer depends on a secret; a malformed explicit `ADMIN_ORIGIN` override still fails closed. Do not publish a repair tool until the signing pair is verified securely.

Android client config may contain only the Supabase project URL, public publishable/anon key and RSA public key. Never embed a service-role key or signing private key in the APK.

## 3. Owner authentication

**Web Owner Admin**
1. Open `https://mahi-repair-admin.onrender.com/login`.
2. Complete a normal login with the existing configured Owner Auth identity and its current security PIN.
3. Confirm the server verifies the configured Owner identity, `profiles.role='owner'`, and `profiles.status='active'`.
4. Verify a Member session is denied by protected routes and APIs.

**Android Owner console**
1. Sign in to the Android app with the existing active Owner account.
2. Open the Owner console.
3. The console calls `get-admin-dashboard`; the Edge Function validates the signed-in JWT and active Owner profile before returning data.
4. Verify Members cannot load the dashboard or call Owner mutations.

No automated or unauthorized sign-in was attempted. A real Owner login has not yet been recorded as a passing production test. Do not change the existing Owner or Member roles to troubleshoot login.

## 4. Member email and device enrollment

1. Configure the deployed Auth Send Email Hook only after adding the webhook secret, Resend/Brevo API keys and a sender verified in both providers; then enable the Hook under Supabase Authentication → Hooks. Until then, leave the current Auth email sender unchanged. Also verify redirect URLs and rate limits.
2. Invite only a real Member from Owner Admin. Member invitation cannot assign the Owner role.
3. The Member signs in to Android with the emailed OTP.
4. Android obtains a server-issued device UUID through `register-device`.
5. Android then submits the consented hardware profile, dynamic health telemetry and field-availability map to the same authenticated function.
6. Confirm Owner Admin shows the actual device model/SoC, Android/app versions, RAM/storage/battery/thermal values and profile-sync timestamp.
7. Suspend the Member or revoke the device and verify subsequent catalogue/report requests are rejected.

The database schema and endpoint are deployed, but the production project currently has 0 registered devices. The Resend/Brevo hook is deployed but not connected in Auth because provider keys, verified sender and the dashboard-generated hook secret are not configured through the available tools. OTP delivery and physical-device telemetry persistence have not been verified. No test emails were sent.

## 5. Tool publishing and signed recipe verification

1. Confirm the server signing private key matches the Android RSA public key.
2. Create a tool in Owner Admin.
3. Validate its JSON against the allowlisted recipe/action schema in the Admin Test Lab.
4. Publish; verify a canonical payload and RSA-SHA256 signature are committed as an immutable version.
5. Assign the tool to one real Member or all active Members.
6. Android must fetch only current published, assigned and compatible versions, verify the signature before parsing/execution, and reject revoked or incompatible content.
7. Verify disable, draft deletion, version conflict, assignment change and device revocation.

Production currently has 0 tools and 0 published versions. No production signing-key match or signed tool round trip has been verified.

## 6. Repair execution, reports and Realtime

1. Run a safe read-only diagnostic on a physical supported Android device.
2. Confirm native action status and user confirmation behavior.
3. Submit one repair report; confirm the record appears in Owner Admin.
4. Replay the same report ID/content and verify idempotent duplicate success.
5. Reuse the report ID with changed content or another identity and verify conflict rejection.
6. Verify private Realtime invalidations reach only the authorized Owner console or the Member's own device topic.
7. Revoke a device and verify the next catalogue/report request is denied.

The private Realtime policies/triggers and transactional report functions are deployed. Actual event delivery on physical Android and browser clients has not been demonstrated. Production currently has 0 repair runs.

## 7. Android release signing

The regular Android CI job creates a **debug-signed** APK. It is for development/testing and is not the production release artifact.

A manual workflow named **Android Production Release** now builds a signed APK and AAB from the app's existing upload keystore. Configure these GitHub repository Actions secrets before running it:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_PASSWORD`

The current Gradle release configuration expects the existing keystore alias `upload`. Do not generate or substitute a new keystore for an app already distributed; the signer must remain compatible with the published app. The signed release workflow has not yet been run successfully.

## 8. Legacy API, AI and remote support

- The standalone legacy Express API uses a file-backed `MemoryStagingDb` and demo seed records. It is not production storage and must not be deployed to an ephemeral Render instance.
- Android device telemetry and Owner management no longer rely on `MAHI_API_BASE_URL`.
- The Gemini Admin assistant does not have a configured production AI provider endpoint in this build. Do not treat AI actions as available.
- The legacy `RemoteSupportService` still depends on Express remote-support routes backed by the file database. The Android notification explicitly says live video transport is not configured. It is not a production remote-control feature and must remain fail-closed.

A production remote-support implementation needs durable session/command/signaling storage, authenticated participant checks, user-consented MediaProjection, WebRTC media transport, command signature/expiry/replay validation, rate limits, audit, and physical-device/browser tests.

## 9. Security, operations and final gate

- Supabase Security Advisor reports leaked-password protection disabled. The project is on the Free plan; the feature is plan-limited and remains unresolved.
- Supabase Health Advisor currently reports no health lints. The foreign-key covering index warning was fixed; remaining unused-index notices are informational while the tables are empty. Do not remove indexes blindly.
- Render Admin runs on the Free plan and can suspend/sleep; this is not a high-availability production configuration.
- Auth Send Email Hook activation, Resend/Brevo sender verification, live Auth redirect settings, Supabase global Realtime public-access toggle, backup/restore, alerting, and signing-key rotation procedures still need operator verification.
- Actual Owner login, Member OTP, physical device enrollment, telemetry persistence, signed publication, report submission, Realtime delivery, and release APK/AAB installation remain acceptance tests.

**Release decision: NOT FULLY PRODUCTION READY.** Infrastructure and code paths are substantially improved, but the credential, signing, email, remote-support, and physical-device gates above must pass before calling this production-ready.
