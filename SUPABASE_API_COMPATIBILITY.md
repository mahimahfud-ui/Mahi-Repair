# Mahi Repair — Supabase API & Backend Compatibility

**Verified:** November 8, 2026  
**Supabase project:** `shwqsljicmhflnvjqjah`  
**Project URL:** `https://shwqsljicmhflnvjqjah.supabase.co`  
**Admin repo / branch:** `mahimahfud-ui/Mahi-Repair-Admin` / `main`  
**Admin commit:** `6b30f2f200aa64423c5333878f90f4edbf62030d`  
**Android repo / main:** `mahimahfud-ui/Mahi-Repair` / main; compile/config fixes are in draft PR #13 and are not yet merged.  
**Render preview:** `https://mahi-repair-admin.onrender.com`

## 1. Architecture

The Admin web console and the Android app are independent Supabase clients. The Android app does not call the Render website. Both clients call the same Supabase URL with the public publishable key and authenticated user sessions.

- Admin: private Owner web console; server-side active Owner checks; read-only browser database access; allowlisted Owner-only Edge Function mutations.
- Android: active Member sessions; registered-device identity; signed recipe retrieval; device-scoped private Realtime; run-report upload.
- Backend: shared PostgreSQL schema, RLS, Auth, private Broadcast policies, and 15 JWT-verified Edge Functions.

The service-role key and RSA-3072 signing private key remain inside Supabase Edge Functions. They must never be copied to Render, Android resources, or browser variables.

## 2. Android HTTP contract

| Endpoint | Request | Expected result |
|---|---|---|
| `register-device` | `device_id?`, `device_label?`, `app_version`, `android_version`, `android_api` | `data.device_id`, `data.registered=true` |
| `get-my-repair-tools` | `device_id` | Array of currently published, assigned, device-compatible signed recipes |
| `get-signed-repair-tool` | `tool_id`, `device_id` | Current signed payload, signature, algorithm and version |
| `submit-repair-run` | `run_id`, `device_id`, `tool_id`, `tool_version`, timestamps, result, step results and app version | `data.run_id`, `data.accepted=true`, `data.duplicate` |

All calls require a valid Supabase JWT. A member must be active, the device must belong to that member and not be revoked, the tool must be currently published and assigned, and the app/Android version must meet the signed recipe's compatibility requirements.

## 3. Report identity and idempotency

Android's client-generated `run_id` is stored as `public.repair_runs.id` (the table's primary key); it is not a separate table column.

The deployed `submit-repair-run` function computes a SHA-256 fingerprint from normalized report content and authenticated identity, then calls the transactional `submit_repair_run_idempotent` SQL function:

- A new authorized report is stored and returns HTTP 201 with `duplicate=false`.
- An exact retry from the same member/device returns HTTP 200 with `duplicate=true`.
- Reusing the UUID with different content or another identity returns HTTP 409.
- The SQL transaction locks the member/device/tool/assignment/version state while validating and accepting the report.

This closes the previous production mismatch where the deployed function rejected Android's `run_id` field.

## 4. Atomic device and catalogue RPCs

The deployed member APIs use database transactions rather than assembling authorization from independent, race-prone reads:

- `register_device_for_member`
- `get_member_repair_catalog`
- `get_signed_repair_tool_for_device`
- `submit_repair_run_idempotent`

They re-check account status, device ownership/revocation, publication/assignment, current version and device compatibility in the database transaction. Browser/Android clients do not receive direct write grants.

## 5. Private Realtime contract

### Android member-device topics

Topic format:

`mahi-repair:member:{userId}:device:{deviceId}`

Only the active member whose authenticated identity and non-revoked device exactly match that topic may subscribe. Events are opaque invalidations, for example:

- `catalog_changed`
- `member_access_changed`
- `device_revoked`

Android uses the event as a hint to re-fetch the authorized catalogue or revalidate access. A Realtime message never grants execution permission.

### Admin Owner topic

Topic:

`mahi-repair:admin`

Only an authenticated profile with `role='owner'` and `status='active'` may subscribe. Database triggers send `admin_data_changed` with an opaque `event_id` after Admin-visible writes. No row data, PII, recipes or signatures are included.

The Next.js console listens to that private topic and calls `router.refresh()`. If the websocket is disconnected, a 15-second fallback refresh continues until reconnection.

### Live update verification boundary

The production migrations, two private-channel RLS policies, and broadcast triggers are deployed. The Render Admin service is live and the Admin CI has passed. However, the database currently has no registered Android devices, no tools, and no repair runs. Therefore actual event delivery to a physical Android device and the Owner browser has not yet been end-to-end demonstrated. Register a test device and publish a test tool before recording that acceptance as complete.

## 6. Production deployment evidence

All seven repository migrations are recorded in the Supabase production migration history (the remote versions are shown here):

| Remote version | Source migration |
|---|---|
| `20261105171141` | `202611050001_initial_schema.sql` |\n| `20261105171330` | `20261105180000_harden_rls_grants_and_indexes.sql` |\n| `20261105171827` | `20261105183000_deny_browser_rate_limit_access.sql` |\n| `20261106170418` | `20261106120000_private_realtime_catalog_broadcast.sql` |
| `20261106170438` | `20261106153000_backend_report_idempotency_and_atomic_catalog.sql` |
| `20261106170450` | `20261106160000_transactional_admin_mutations_audit.sql` |
| `20261106170509` | `20261106170000_atomic_member_catalog_and_device_registration.sql` |

All 15 Edge Functions were redeployed with `verify_jwt=true` and verified ACTIVE. Important deployed versions:

| Function | Version |
|---|---:|
| `get-my-repair-tools` | 4 |
| `get-signed-repair-tool` | 4 |
| `register-device` | 3 |
| `submit-repair-run` | 4 |
| Admin mutation functions | 2–3 |
| `get-admin-analytics` | 3 |

Admin CI for merge commit `6b30f2f200aa64423c5333878f90f4edbf62030d` passed. Render deployment for that commit was started and is being verified; do not treat it as LIVE until its deployment status is `live`.

Android draft PR #13 updates `.env.example` with the public Supabase project URL/publishable key and RSA-3072 public key. The PR is not merged; the production Android main branch has not yet received this configuration.

## 7. Remaining production acceptance checks

The database currently reports 1 active Owner and 1 active Member, but 0 registered devices, 0 tools and 0 repair runs. Complete these checks with a real Owner browser session and a test Android installation:

1. Verify the normal Owner PIN sign-in against the existing configured Owner Auth identity; Member OTP delivery must be verified separately.
2. Confirm Supabase Edge Function secret `ADMIN_ORIGIN` is exactly `https://mahi-repair-admin.onrender.com` (canonical HTTPS origin, no slash or path).
3. Confirm `RECIPE_SIGNING_PRIVATE_KEY_PEM` exists in Supabase and its public-key counterpart configured in Android is the matching RSA-3072 key.
4. Confirm Supabase Realtime global “Allow public access” is disabled.
5. After the signing-key match is securely confirmed, sign in as Owner, create/publish a test recipe, invite a Member, register a device and verify its private channel subscription.
6. Publish, disable, assign/unassign a test tool; verify Android and Admin receive invalidations and then re-fetch fresh authorized data.
7. Submit one run report; replay the same UUID/content and expect duplicate success; alter its content or user identity and expect conflict.
8. Revoke the device/member and verify the next authenticated fetch/report attempt is rejected.

Auth URL configuration, Edge Function secret values, SMTP delivery and the global Realtime dashboard toggle cannot be inspected through the connected Supabase API tools used for this audit. SMTP should be verified separately before relying on magic-link invitations. Do not claim physical-device live-event acceptance until the above checks pass.

## 8. Security boundaries

- Only the Supabase public publishable key is in Android/Render client configuration.
- No service-role key or RSA signing private key is in GitHub or the client applications.
- All Edge Functions are JWT verified; Owner operations separately verify the active Owner profile.
- RLS remains enabled; browser database writes remain revoked.
- Realtime messages are private, opaque invalidation hints; authorization is always re-checked on the next API request.
