# Mahi Repair — Android App & Owner Admin

Mahi Repair is one repair-management product with two clients: a native Android app and a responsive Owner Admin website. Both clients must use one identity system, one authorized backend, one tool catalogue and shared versioned API/recipe contracts.

## Repository structure and source of truth

- **Android app:** `app/` — Kotlin and Jetpack Compose.
- **Canonical Owner Admin website:** `admin-panel/` — Next.js App Router, TypeScript and Tailwind, maintained in this same repository.
- **Shared recipe schema:** `admin-panel/schemas/repair-recipe-v1.schema.json`.
- **Cloud Run API contract:** `docs/CLOUD_RUN_API_CONTRACT.md`.
- **Admin Cloud Run client:** `admin-panel/src/lib/api/cloud-run.ts` with tests in `admin-panel/tests/cloud-run-client.test.ts`.
- **Android signature/runtime boundary:** `app/src/main/java/com/example/security/`.
- **Legacy staging surface:** root `src/` — contains older React/Express code and a file-backed demo database; it is not production storage.
- **Secondary reference repo:** [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin) contains overlapping Admin-era code. Do not deploy it as a competing Admin/backend. Port only missing UI/features into this repository's `admin-panel/`.

## Backend migration status

The current shipped source still contains Supabase-based Android and Admin clients. Firebase project configuration, Firestore rules and a data blueprint are already present, but that does **not** mean the production app has been migrated. Google AI Studio is intended to implement and verify the Firebase + Cloud Run backend migration.

- Firebase project configuration is recorded in `firebase-applet-config.json`; existing Firestore rules are in `firestore.rules`; model definitions are in `firebase-blueprint.json`.
- Android currently calls Supabase through `SupabaseClient.kt`, `SupabaseOwnerAdminApiClient.kt` and `SupabaseRealtimeClient.kt`.
- The canonical Admin website currently calls Supabase through `admin-panel/src/lib/supabase/` and server actions.
- Do not switch production traffic, remove Supabase, disable Render, create another Firebase database, or change account roles until the acceptance checklist passes.

**Read before continuing:** [Google AI Studio Handoff](docs/GOOGLE_AI_STUDIO_HANDOFF.md), [Cloud Run API Contract](docs/CLOUD_RUN_API_CONTRACT.md) and [Google Backend Cutover Checklist](docs/GOOGLE_BACKEND_CUTOVER_CHECKLIST.md). The API client and its tests are already implemented for the Admin website; the remaining Google-side work is Firebase Auth/session integration, Cloud Run handlers, persistent Firestore storage, deployment and end-to-end validation.

## Android native action boundary

The app executes a fixed allowlist of native diagnostics and guided repair actions. A signed recipe cannot provide arbitrary shell code or bypass the native action allowlist. Destructive or permission-dependent actions require foreground user confirmation.

## Configure an Android build

1. Copy `.env.example` to `.env` only for local overrides.
2. The current Android binary remains on the legacy Supabase client until the Google migration is complete; do not remove those values before the Firebase client migration and rollback gate pass.
3. `RECIPE_SIGNING_PUBLIC_KEY_PEM` is public verification material only. The replacement private signing key must live in Google Secret Manager and the public key embedded in the APK must match it.
4. Configure `MAHI_API_BASE_URL` with the Cloud Run service URL only after Google deploys the API contract in `docs/CLOUD_RUN_API_CONTRACT.md`. Never point it at the Admin website or the legacy file-backed Express API.

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

## Last documented Supabase baseline — November 8, 2026 (re-verify before cutover)

- Supabase project `shwqsljicmhflnvjqjah` is `ACTIVE_HEALTHY`.
- 9 migrations are applied; all 17 Edge Functions are ACTIVE. Sixteen application functions use JWT verification; `send-email-hook` uses signed Standard Webhooks verification and is not yet connected in Auth.
- RLS is enabled on all 9 public application tables.
- Render Admin's latest merged code deployment is LIVE.
- Android telemetry and Owner-console code changes are merged to main.
- Production currently contains 1 active Owner profile and 1 active Member profile, with 0 registered devices, 0 repair tools, 0 published versions and 0 repair runs.

The Supabase config source now declares the production Admin site URL, but live Auth URL settings were not overwritten through an unavailable management API. The Auth email hook is deployed but not activated pending secrets and sender verification.

Infrastructure deployment is not equivalent to end-to-end acceptance. Owner login, Member OTP delivery, signing-key match, physical Android enrollment, a published signed tool, a persisted repair report, private Realtime delivery, production release signing and remote support still require verification.
