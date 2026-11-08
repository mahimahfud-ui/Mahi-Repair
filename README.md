# Mahi Repair — Android Repair Client & Owner Admin

Mahi Repair is a recipe-driven Android diagnostics and guided repair system. The supported cloud path uses a separate Owner Admin application and one shared Supabase project.

## Production architecture

- **Android Member app** — native Kotlin/Jetpack Compose client. Members authenticate by email OTP, register their Android device, fetch only assigned and compatible tools, verify detached RSA-SHA256 signatures locally, run allowlisted native actions and submit repair reports.
- **Owner Admin** — separate Next.js application in [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin), hosted at [mahi-repair-admin.onrender.com](https://mahi-repair-admin.onrender.com). Owner sign-in uses the configured Owner Auth account and server-side PIN verification; every protected route checks the active Owner profile and configured Owner identity.
- **Supabase** — shared Auth, PostgreSQL, RLS, private Realtime and 15 JWT-protected Edge Functions. Android calls Supabase directly; it does not use the Admin website as an API.
- **Recipe signing** — RSA-3072 / SHA-256. The private signing key belongs only in Supabase Edge Function secrets. The Android app receives only the public key at build time.

## Android native action boundary

The app executes a fixed allowlist such as connectivity/DNS/HTTPS checks, app/package checks, storage checks, guided settings navigation, help instructions and permission requests. A signed recipe cannot provide arbitrary shell code or bypass the native action allowlist. Guided actions and permission requests require user confirmation.

## Configure an Android build

1. Copy `.env.example` to `.env` only when local overrides are needed.
2. Keep the production Supabase URL and public publishable key as client configuration. Never put a service-role key, PIN, password or private signing key in this file.
3. `RECIPE_SIGNING_PUBLIC_KEY_PEM` is public material. It must match the private key configured in the production Supabase signing function.
4. The optional legacy `MAHI_API_BASE_URL` and remote-support endpoints intentionally use fail-closed placeholder values. Do not replace them with the Admin website URL; the old Express API is not a production backend.

Build and test the Android debug app:

```bash
cd app
./gradlew testDebugUnitTest assembleDebug
```

CI currently builds a debug APK. A production release APK/AAB requires the existing, correct upload keystore and signing credentials; do not generate a replacement key unless an app-signing migration is planned.

## Local web/legacy backend

The repository also contains a legacy React/Express development surface. Its current backend data adapter is a file-backed `MemoryStagingDb` with demo seeds; it is not production storage and must not be deployed to an ephemeral Render instance. The production Owner Admin and Member app use the shared Supabase schema and Edge Functions instead.

## Verified production baseline — 8 November 2026

- Supabase project `shwqsljicmhflnvjqjah` is ACTIVE_HEALTHY.
- All 7 production migrations are applied and all 15 Edge Functions are ACTIVE with JWT verification enabled.
- RLS is enabled on all public tables.
- Existing Owner `mahinmhfd@gmail.com` remains active Owner; `mahimahfud@gmail.com` remains active Member.
- Production data currently contains 0 registered devices, 0 repair tools, 0 published versions and 0 repair runs.

This means the infrastructure is provisioned, but real Member enrolment, a published signed tool, a persisted repair report, private Realtime delivery and physical Android acceptance have not yet been demonstrated. Do not describe the app as fully production-ready until those checks pass.
