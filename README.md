# Mahi Repair

Mahi Repair is a private Android repair toolkit for the owner's family and trusted friends. A separate owner-only Admin Panel manages signed repair recipes and member/device access through Supabase.

## Architecture

- **Android app (`app/`)** — native Kotlin/Jetpack Compose repair client. The home screen has one primary **RUN REPAIR** action; the published-tool picker appears only after the user taps it. Invite-only email OTP remains enabled.
- **Admin Panel (`admin-panel/`)** — separate Next.js owner console for drafting, validating, testing, publishing recipes, managing members and devices, and viewing repair reports. It is not embedded in the Android UI.
- **Supabase** — Auth, PostgreSQL/RLS, private Realtime Broadcast and 15 JWT-protected Edge Functions.

## Security and signed recipes

- The canonical recipe contract is `admin-panel/schemas/repair-recipe-v1.schema.json`.
- The Admin Panel canonicalizes JSON and signs the exact UTF-8 bytes with RSA-3072 / RSA-SHA256 (RSASSA-PKCS1-v1_5).
- Android verifies the detached signature before parsing or executing the recipe.
- The APK contains only the matching public verification key. The private PKCS#8 signing key belongs only in Supabase Edge Function Secrets.
- Android access/refresh tokens are encrypted with an Android Keystore-backed AES-GCM key. Refresh-token rotation is serialized.
- A recipe is declarative JSON, not executable code. The APK runs only native action IDs compiled into the app. No remote shell, scripts, DEX, JAR, APK or arbitrary code are executed.

## One-install catalogue updates

After the APK is installed and the device is authorized, the owner can publish new recipes that combine existing allowlisted native actions. Android receives a private per-device Realtime notification, fetches the currently authorized signed catalogue, verifies each signature and updates Room.

Realtime notifications are active while the app is foregrounded. App launch, foreground/resume, reconnect and periodic WorkManager sync recover missed events. A new native Kotlin feature, Android permission or unsupported OS capability still requires an APK update.

## Important paths

- Canonical recipe schema: `admin-panel/schemas/repair-recipe-v1.schema.json`
- Canonical catalog/serializer: `admin-panel/src/lib/recipe/`
- Supabase migrations: `admin-panel/supabase/migrations/`
- Supabase Edge Functions: `admin-panel/supabase/functions/`
- Android signature verifier contract: `admin-panel/android-contract/RecipeSignatureVerifier.kt`
- Android implementation: `app/src/main/`
- Shared contract fixtures: `contract-tests/`
- Deployment/security runbooks: `admin-panel/docs/`

## Local development

Admin Panel:

```bash
cd admin-panel
npm ci
cp .env.example .env.local
npm run dev
```

Android (when the Gradle wrapper is available in your local environment):

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The root `.env.example` has a placeholder public signing key. Production recipe execution requires the real X.509 public key matching the private key configured directly in Supabase.

## Current deployment status

- Render preview: https://mahi-repair-admin-preview.onrender.com
- GitHub PR #1: https://github.com/mahimahfud-ui/Mahi-Repair/pull/1

The PR remains draft/open pending final Android CI, production secrets, owner bootstrap, production migration/function deployment and real-device acceptance tests. A live Render preview does not mean the production Supabase release is complete.
