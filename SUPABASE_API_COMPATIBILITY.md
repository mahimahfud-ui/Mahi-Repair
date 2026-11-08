# Mahi Repair — Supabase API & Backend Compatibility

**Verified:** November 8, 2026  
**Project:** `shwqsljicmhflnvjqjah` (`ap-south-1`, PostgreSQL 17)  
**Admin:** [mahi-repair-admin.onrender.com](https://mahi-repair-admin.onrender.com)  
**Source repositories:** `mahimahfud-ui/Mahi-Repair` and `mahimahfud-ui/Mahi-Repair-Admin`

## 1. Architecture

The Admin website and Android app are separate Supabase clients. Android does not call the Render Admin website as an API.

- Admin web: configured Owner identity, server-side session guards, Owner-only server mutations.
- Android Member client: email OTP, server-issued device UUID, signed repair catalogue, native allowlisted executor and idempotent report upload.
- Android Owner console: the authenticated Owner JWT calls the Owner-only `get-admin-dashboard` Edge Function and Owner mutation functions.
- Supabase: shared Auth, PostgreSQL, RLS, service-role RPCs, private Realtime and 17 Edge Functions (16 JWT-protected app/API functions plus one signed Auth email webhook).

The service-role key and RSA-3072 signing private key stay in Supabase Edge Function secrets. Neither may be included in the browser or Android APK.

## 2. Supabase Auth email hook

The `send-email-hook` Edge Function is ACTIVE (v1) and validates the Standard Webhooks signature from Supabase Auth. It sends OTP/magic-link messages through Resend first and falls back to Brevo when the primary provider is unavailable or returns failure. The function has `verify_jwt=false` only because it uses the Auth webhook signature as its authentication mechanism; it is not a public email-sending endpoint.

The function is deployed but **not connected** in Auth settings. Before activation, configure `SEND_EMAIL_HOOK_SECRET`, `RESEND_API_KEY`, `BREVO_API_KEY` and `AUTH_EMAIL_FROM` in Edge Function secrets; verify the sender in both providers; then select the HTTPS Send Email Hook URL under Supabase Authentication → Hooks. The current live Auth hook/sender settings were not modified. No test emails were sent.

## 3. Android Member API contract

All requests require a valid Supabase JWT and the public client key in the `apikey` header.

| Function | Request | Result |
|---|---|---|
| `register-device` | `device_id?`, `device_label?`, `app_version`, `android_version`, `android_api`, `hardware_profile?`, `telemetry?`, `field_availability?` | Server-issued/confirmed `device_id`, `registered=true`, `profile_updated` |
| `get-my-repair-tools` | `device_id` | Only currently published, assigned, compatible signed recipes |
| `get-signed-repair-tool` | `tool_id`, `device_id` | Current signed payload, signature, algorithm and version |
| `submit-repair-run` | `run_id`, `device_id`, `tool_id`, `tool_version`, timestamps, result, step results and app version | `run_id`, `accepted=true`, duplicate state |

The device profile upload is bounded server-side: hardware/telemetry JSON objects are capped at 32 KB each and field-availability JSON is capped at 8 KB. Allowed availability values are `OBSERVED`, `ESTIMATED`, `UNAVAILABLE`, `PERMISSION_REQUIRED`, and `UNSUPPORTED`.

The function verifies the account is an active Member and the device UUID belongs to that Member and is not revoked. Device metadata, hardware profile, telemetry and heartbeat are updated atomically through `register_device_profile_for_member`. That SQL function is executable only by `service_role`, not `anon` or `authenticated`.

## 4. Owner API contract

All functions below require a valid JWT and re-check that the authenticated profile is active with `role='owner'`. Browser clients do not get direct table write grants.

| Function | Purpose |
|---|---|
| `get-admin-dashboard` | Owner-only summary of analytics, tools, Members, devices (including hardware/health telemetry), recent runs and audit events |
| `admin-invite-member` | Invite a Member; invitation does not grant Owner role |
| `admin-manage-member` | Activate, suspend or revoke a Member |
| `admin-upsert-tool` | Create/update a draft tool |
| `admin-publish-tool` | Validate and atomically publish a signed tool version |
| `admin-disable-tool` | Disable/restore a published tool |
| `admin-assign-tool` | Change Member/all-active-Members access |
| `admin-manage-device` | Revoke/restore a registered device |
| `admin-manage-tool-state` | Move draft/testing/archive state |
| `admin-delete-draft-tool` | Delete a draft tool |
| `admin-save-validation` | Persist Test Lab result/history |
| `get-admin-analytics` | Owner-only analytics aggregation |

The mobile Gemini helper is not connected to a production AI provider. It must not be treated as a working production feature.

## 5. Report identity and idempotency

Android's client-generated `run_id` is stored as `public.repair_runs.id` (the table primary key); it is not a separate table column.

The deployed `submit-repair-run` function computes a SHA-256 fingerprint from normalized report content and authenticated identity, then calls the transactional `submit_repair_run_idempotent` function:

- New report: stored and accepted.
- Exact retry by the same Member/device: duplicate success.
- Reusing the UUID with changed content or another identity: conflict rejection.

The transaction locks and rechecks Member/device/tool/assignment/version state before accepting the report.

## 6. Private Realtime contract

### Member device topic

`mahi-repair:member:{userId}:device:{deviceId}`

Only the active Member whose authenticated identity and non-revoked device exactly match the topic may subscribe. Events are opaque invalidations such as `catalog_changed`, `member_access_changed` and `device_revoked`. A message never grants execution permission; Android re-fetches the authorized catalogue or revalidates access.

### Owner topic

`mahi-repair:admin`

Only an active Owner profile may subscribe. Admin-visible database mutations broadcast opaque invalidation events; the console then re-fetches through server-verified APIs.

Database policies and broadcast triggers are deployed, but actual delivery to a physical Android client and a real Owner browser session has not yet been demonstrated. Current production has no registered devices, tools or runs.

## 7. Production migration and function state

Nine production migrations are applied:

| Remote version | Migration |
|---|---|
| `20261105171141` | `202611050001_initial_schema` |
| `20261105171330` | `20261105180000_harden_rls_grants_and_indexes` |
| `20261105171827` | `20261105183000_deny_browser_rate_limit_access` |
| `20261106170418` | `20261106120000_private_realtime_catalog_broadcast` |
| `20261106170438` | `20261106153000_backend_report_idempotency_and_atomic_catalog` |
| `20261106170450` | `20261106160000_transactional_admin_mutations_audit` |
| `20261106170509` | `20261106170000_atomic_member_catalog_and_device_registration` |
| `20261108161813` | `device_intelligence_telemetry` |
| `20261108162722` | `device_tool_syncs_tool_version_index` |

All 17 Edge Functions are ACTIVE. Sixteen app/API functions have `verify_jwt=true`; the Auth `send-email-hook` has `verify_jwt=false` and verifies the Standard Webhooks HMAC signature. New functions/versions:
- `register-device` v4
- `get-admin-dashboard` v1
- `send-email-hook` v1 (deployed, not connected in Auth)

The two new service-role RPCs, `register_device_profile_for_member` and `get_admin_analytics`, were verified executable by service_role only. RLS remains enabled for `public.devices`; new foreign-key index `device_tool_syncs_tool_version_idx` exists.

## 8. Production data and acceptance gaps

The production database currently contains two profiles (one active Owner, one active Member) and 0 devices, 0 repair tools, 0 published versions, 0 repair runs and 0 audit events.

Still unverified:
1. Actual Owner web login and Android Owner-console login.
2. Member OTP email delivery; the Resend-primary/Brevo-fallback Auth hook still needs API keys, verified sender and the dashboard-generated webhook secret before it can be connected.
3. Live Supabase Auth site URL and redirect allowlist verification.
4. Presence/format of `RECIPE_SIGNING_PRIVATE_KEY_PEM` and cryptographic match with Android public key.
5. Signed tool publication and execution on a physical device.
6. Device telemetry persistence from an actual Member device.
7. Repair report submit/retry/conflict on real accounts.
8. Private Realtime message delivery end to end.
9. Production release signing with the existing upload keystore.
10. Supabase global Realtime public-access setting, backup/restore and alerts.

The connected Supabase tooling does not expose protected secret values, the live Auth Hook/SMTP settings or the global Realtime dashboard toggle. The source `supabase/config.toml` now declares the production Admin site URL, but live Auth URL settings were not overwritten. The application Edge Functions use a hard-coded canonical Admin-origin fallback if an optional `ADMIN_ORIGIN` override is absent. No test sign-ins or emails were sent.

## 9. Security and legacy services

- Supabase RLS is enabled on all 9 public application tables and private Realtime messages.
- Anonymous table grants are absent; authenticated browser grants are read-only.
- Privileged SQL RPCs are restricted to service_role.
- Security Advisor currently reports leaked-password protection disabled. The Supabase organization is on the Free plan; this warning remains unresolved.
- The foreign-key covering index advisor issue was fixed. Remaining unused-index notices are informational while production tables are empty.
- The legacy Express `MemoryStagingDb` is file-backed demo storage and is not production-safe. Remote support still depends on that API and has no production live-video transport.
- The signed release workflow is in GitHub, but no signed APK/AAB has yet been produced because the existing upload-keystore secrets are not verified.

Do not label the app fully production-ready until the real-account, signing, email, device, report and Realtime acceptance checks pass.
