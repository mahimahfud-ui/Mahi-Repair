# Mahi Repair

Mahi Repair is a private Android repair toolkit for the owner's family and trusted friends. A separate owner-only Admin Panel manages signed repair recipes and member/device access through Supabase.

## Architecture

- **Android app (app/)** — native Kotlin/Jetpack Compose repair client. The home screen has one primary **RUN REPAIR** action; the published-tool picker appears only after the user taps it. Invite-only email OTP remains enabled.
- **Admin Panel (admin-panel/)** — separate Next.js owner console for drafting, validating, testing, publishing recipes, managing members and devices, and viewing repair reports. It is not embedded in the Android UI.
- **Supabase** — Auth, PostgreSQL/RLS, private Realtime Broadcast and 15 JWT-protected Edge Functions.

## Security and signed recipes

- The canonical recipe contract is admin-panel/schemas/repair-recipe-v1.schema.json.
- The Admin Panel canonicalizes JSON and signs the exact UTF-8 bytes with RSA-3072 / RSA-SHA256 (RSASSA-PKCS1-v1_5).
- Android verifies the detached signature before parsing or executing the recipe.
- The APK contains only the matching public verification key. The private PKCS#8 signing key belongs only in Supabase Edge Function Secrets.
- Android access/refresh tokens are encrypted with an Android Keystore-backed AES-GCM key. Refresh-token rotation is serialized.
- A recipe is declarative JSON, not executable code. The APK runs only native action IDs compiled into the app. No remote shell, scripts, DEX, JAR, APK or arbitrary code is executed.

## One-install catalogue updates

After the APK is installed and the device is authorized, the owner can publish new recipes that combine existing allowlisted native actions. Android receives a private per-device Realtime notification, fetches the currently authorized signed catalogue, verifies each signature and replaces Room in one transaction. An interrupted sync cannot leave a partially cleared catalogue.

Realtime notifications are active while the app is foregrounded. App launch, foreground/resume, reconnect and periodic WorkManager sync recover missed events. A new native Kotlin feature, Android permission or unsupported OS capability still requires an APK update.

## Repair report delivery

Android commits each repair report and its immutable serialized request to Room before upload. A failed upload remains in a durable outbox. WorkManager retries with network constraints and exponential backoff; a periodic 15-minute worker recovers reports after process death. Each report uses a client-generated UUID as its server idempotency key. Identical retries are acknowledged; conflicting reuse returns HTTP 409.

## Important paths

- Canonical recipe schema: admin-panel/schemas/repair-recipe-v1.schema.json
- Canonical catalog/serializer: admin-panel/src/lib/recipe/
- Supabase migrations: admin-panel/supabase/migrations/
- Supabase Edge Functions: admin-panel/supabase/functions/
- Android signature verifier contract: admin-panel/android-contract/RecipeSignatureVerifier.kt
- Android implementation: app/src/main/
- Shared contract fixtures: contract-tests/
- Deployment/security runbooks: admin-panel/docs/

## Local development

Admin Panel:

    cd admin-panel
    npm ci
    cp .env.example .env.local
    npm run dev

Android:

    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug
    ./gradlew :app:connectedDebugAndroidTest

The root .env.example has a placeholder public signing key. Production recipe execution requires the real X.509 public key matching the private key configured directly in Supabase.

## Current verification and deployment status

Current branch: security-hardening/run-repair  
Current verified CI HEAD: dd7c922bf5533c34a424d8d7994f29d2d0fb0f82.

All three current-head GitHub Actions workflows passed:

- [Android CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251879) — unit tests, debug APK, API 35 Compose emulator tests and source/APK privileged-secret marker scans.
- [Supabase Edge Functions CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251882) — all 15 function entrypoints/shared modules and canonical contract tests.
- [Admin Panel CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251933) — dependency audit, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build.

[Download the Android debug APK artifact](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251879/artifacts/13479439548). It is a CI debug build with an ephemeral debug keystore, not a signed production release. No release APK/AAB link is available yet.

The configured Render service is the Free preview at https://mahi-repair-admin-preview.onrender.com. It is not a production service. Production Supabase migration/function deployment, signing-key provisioning, owner/member/device onboarding and real-device acceptance remain unverified. See [BUILD_STATUS](admin-panel/docs/BUILD_STATUS.md), [PRODUCTION_READINESS](admin-panel/docs/PRODUCTION_READINESS.md), [Render deployment](admin-panel/docs/RENDER_DEPLOYMENT.md) and [setup/integration](SETUP_AND_INTEGRATION_GUIDE.md).

PR #1 remains draft, open and unmerged: https://github.com/mahimahfud-ui/Mahi-Repair/pull/1. Do not merge or declare production readiness until all production gates pass.
