# Android Backend Integration — Current Supabase Contract

**This document replaces the older Firebase/Cloud Run staging guide.** The production architecture currently used by Android and the separate Owner Admin website is one shared Supabase project. Do not use the old Firebase ID-token, Cloud Run `/api/v1`, in-app Admin PIN or legacy Express contracts described in historical branches.

## Canonical resources

| Component | Current value |
|---|---|
| Android repository | [Mahi-Repair](https://github.com/mahimahfud-ui/Mahi-Repair) |
| Standalone Owner Admin | [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin) |
| Owner website | https://mahi-repair-admin-yy7c.onrender.com |
| Supabase project ref | `shwqsljicmhflnvjqjah` |
| Supabase URL | `https://shwqsljicmhflnvjqjah.supabase.co` |
| Region | `ap-south-1` |

The Android APK is Member-only. Owner administration must stay in the separate website. Do not re-add Owner screens, Admin routes, Owner API clients or an Admin PIN to Android.

## Android client configuration

The Android build reads the public Supabase URL and publishable key from the Gradle-generated `BuildConfig` values sourced from the repository's `.env.example` / local `.env`.

- `SUPABASE_URL`: public Supabase project URL.
- `SUPABASE_ANON_KEY`: public publishable/anon client key.
- `RECIPE_SIGNING_PUBLIC_KEY_PEM`: RSA-3072 verification public key only.
- `MAHI_API_BASE_URL` and `REMOTE_SUPPORT_API_BASE_URL`: legacy optional APIs; their current `.invalid` values intentionally fail closed.

Never put a Supabase service-role/secret key, Owner PIN, SMTP/provider credential or RSA private key in Android configuration.

## Authentication and API flow

The active Kotlin client is `app/src/main/java/com/example/data/remote/SupabaseClient.kt`.

1. Member email OTP request uses Supabase Auth `/auth/v1/otp` with account creation disabled.
2. OTP verification uses Supabase Auth `/auth/v1/verify`.
3. Session refresh and sign-out use Supabase Auth token/logout endpoints.
4. Device registration calls the JWT-protected `register-device` Edge Function and stores only the server-issued device UUID in app-private storage.
5. Catalogue sync calls `get-my-repair-tools` for the authenticated Member and registered device.
6. Single-tool retrieval calls `get-signed-repair-tool`; Android verifies RSA-SHA256 over the exact `signed_payload` UTF-8 bytes before parsing.
7. Android validates schema, parameters and every action ID against the native allowlist. Downloaded data is never executable code.
8. Repair reports are stored in the Room outbox and uploaded through `submit-repair-run`; retries reuse the same `run_id` so the backend can enforce idempotency.

All Member endpoints require the Supabase access token. Owner tokens must be rejected by Member-only operations; suspended Members and revoked devices must fail closed.

## Realtime contract

The Android client uses `SupabaseRealtimeClient.kt` to subscribe to the private topic derived from the authenticated Member UID and server-issued device ID. Events are opaque invalidation hints only. On an event, Android refetches the current authorized catalogue through authenticated Edge Functions; a Realtime event never carries a recipe or grants permission to execute.

The Admin website uses the separate Owner-only topic and its server-guarded pages. Keep project-wide public Realtime access disabled and verify the corresponding `realtime.messages` RLS policies.

## Recipe signing security

The Android app contains only the RSA public verification key. The matching RSA-3072 PKCS#8 private key belongs in Supabase Edge Function Secrets and must be used only by the server-side `admin-publish-tool` function.

The live production keypair match was not independently verifiable during the 20 November 2026 audit. Do not publish a real repair tool until the Owner securely verifies that the deployed private key matches the Android public key. Never print or share the private key.

## Authoritative contracts and acceptance

Read these before changing code or production configuration:

- [Production handover and live acceptance gates](PRODUCTION_SETUP_CHECKLIST.md)
- [Android API contract](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/ANDROID_API_CONTRACT.md)
- [Admin deployment and Supabase operations](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/DEPLOYMENT.md)
- [Admin security model](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/SECURITY_MODEL.md)

A green Android build, active Edge Function, or successful unauthenticated health probe is not end-to-end acceptance. The required physical-device sequence is invited Member OTP → server-issued device registration → Owner creates/tests/publishes/assigns a signed tool → Android verifies and runs a safe native action → report appears in Admin Logs.

## Optional services are not part of the core live path

Device Intelligence upload and Remote Support currently point at legacy `MAHI_API_BASE_URL` placeholders and intentionally fail closed. Do not point them at the Admin website or deploy the old local-JSON staging Express backend as production storage. Implement a separate durable authenticated API before enabling those optional features.

## Firebase/Cloud Run material

Firebase project metadata and the old Google AI Studio handoff files remain in the repository for historical/staging reference. They do not define the current Android Auth, catalogue, reporting or Realtime backend. Do not create a new Firebase project/database, provision Cloud Run, enable billing or migrate the clients as part of routine repair-app completion.
