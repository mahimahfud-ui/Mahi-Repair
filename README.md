# Mahi Repair — Android Repair Client & Owner Admin

Mahi Repair is a recipe-driven Android diagnostics and guided-repair system. The supported cloud path uses a separate Owner Admin application and a shared Supabase project.

## Production architecture

- **Android app** — Kotlin/Jetpack Compose client. Members authenticate with email OTP, register their own Android device, synchronize consented hardware/health telemetry, fetch only assigned and compatible repair tools, verify detached RSA-SHA256 signatures locally, execute allowlisted native actions and submit repair reports.
- **Owner Admin** — separate Next.js application in [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin), hosted at [mahi-repair-admin.onrender.com](https://mahi-repair-admin.onrender.com). The web console verifies the configured Owner identity server-side. The Android Owner console uses a signed-in Supabase session; Owner-only Edge Functions enforce the active Owner role on every protected request.
- **Supabase** — shared Auth, PostgreSQL, RLS, private Realtime and 16 JWT-verified Edge Functions. Android calls Supabase directly; it does not use the Admin website as an API.
- **Device intelligence** — `register-device` accepts the authenticated Member's server-issued device UUID, hardware profile, dynamic telemetry and field-availability map. The service-role RPC validates profile ownership and revocation before persisting it.
- **Recipe signing** — RSA-3072 / SHA-256. The signing private key belongs only in Supabase Edge Function secrets. Android receives only the public key at build time.

## Android native action boundary

The app executes a fixed allowlist of native diagnostics and guided repair actions. A signed recipe cannot provide arbitrary shell code or bypass the native action allowlist. Destructive or permission-dependent actions require foreground user confirmation.

## Configure an Android build

1. Copy `.env.example` to `.env` only for local overrides.
2. Use the production Supabase URL and public publishable/anon key as client configuration.
3. `RECIPE_SIGNING_PUBLIC_KEY_PEM` is public material. It must match the private key configured in Supabase.
4. The optional legacy `MAHI_API_BASE_URL` and remote-support endpoints remain fail-closed placeholders. Do not point them at the Admin website URL; the legacy Express API is not production storage.

Run Android unit tests and build a debug APK:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Production release

A manual GitHub Actions workflow, **Android Production Release**, builds a signed APK and AAB using the app's existing upload keystore. It requires these protected repository secrets:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_PASSWORD`

The expected keystore alias is `upload`. Do not replace the signer for an app already distributed. The workflow is merged, but a signed release artifact has not yet been produced because the existing keystore secrets are not configured/verified.

## Legacy components and unavailable services

The repository contains a legacy React/Express development surface. Its backend data adapter is a file-backed `MemoryStagingDb` with demo seeds; it is not production storage and must not be deployed to an ephemeral Render instance.

The mobile Owner dashboard is connected to Supabase. The Gemini admin assistant has no configured production provider endpoint and is not an available production feature. The old remote-support service still depends on the file-backed Express routes and has no configured live video transport; it is not a production remote-control feature.

## Verified production baseline — November 8, 2026

- Supabase project `shwqsljicmhflnvjqjah` is `ACTIVE_HEALTHY`.
- 9 migrations are applied; all 16 Edge Functions are ACTIVE with JWT verification enabled.
- RLS is enabled on all 9 public application tables.
- Render Admin's latest merged code deployment is LIVE.
- Android telemetry and Owner-console code changes are merged to main.
- Production currently contains 1 active Owner profile and 1 active Member profile, with 0 registered devices, 0 repair tools, 0 published versions and 0 repair runs.

Infrastructure deployment is not equivalent to end-to-end acceptance. Owner login, Member OTP delivery, signing-key match, physical Android enrollment, a published signed tool, a persisted repair report, private Realtime delivery, production release signing and remote support still require verification.
