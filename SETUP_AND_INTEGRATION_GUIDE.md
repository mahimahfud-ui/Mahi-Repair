# Mahi Repair — setup and integration guide

## Product layout

1. **Android app:** a single **RUN REPAIR** action on the home screen. Tapping it opens the currently published and authorized repair-tool picker.
2. **Admin Panel:** a separate private Next.js owner console. It drafts, validates, tests, signs and publishes recipes, and manages invited members and registered devices.
3. **Supabase backend:** Auth, PostgreSQL/RLS, private Realtime Broadcast and Edge Functions.

The Android app does not expose Admin Panel navigation, credentials, signing controls or backend management screens.

## Android authentication and storage

- Invite-only Supabase email OTP; account creation is disabled.
- Device registration returns a server-issued device UUID. The app persists it and supplies it to authorized catalogue/run calls.
- Access and refresh tokens are encrypted with Android Keystore-backed AES-GCM in app-private storage.
- Session refresh is serialized to protect rotating refresh tokens. Expired or rejected sessions fail closed.
- Room stores only successfully signature-verified catalogue data and repair history. Server-side authorization remains authoritative.

## Canonical signed recipe contract

The Admin Panel is the source of truth:

- Schema: admin-panel/schemas/repair-recipe-v1.schema.json
- Catalog/canonical serializer: admin-panel/src/lib/recipe/
- Edge Function validator/signer: admin-panel/supabase/functions/_shared/
- Android verifier contract: admin-panel/android-contract/RecipeSignatureVerifier.kt

The Admin Panel signs canonical JSON with RSA-3072 / RSA-SHA256 (RSASSA-PKCS1-v1_5). Android verifies the exact UTF-8 bytes before parsing. Do not parse and reserialize before verification.

The APK embeds only the matching X.509 public key. Keep the PKCS#8 private key only in Supabase Edge Function Secrets. Never put it in Android resources, GitHub, Render or the browser.

## Existing native action allowlist and one-install behavior

The installed APK implements these 13 native actions:

CHECK_INTERNET, DNS_LOOKUP, HTTPS_PROBE, CHECK_APP_INSTALLED, CHECK_STORAGE, OPEN_APP, OPEN_APP_DETAILS, OPEN_ANDROID_SETTINGS, SHOW_GUIDE, ASK_USER_CONFIRMATION, REQUEST_OWN_APP_PERMISSION, VERIFY_INTERNET, VERIFY_DNS.

The owner can publish additional recipes combining these actions without requiring users to reinstall the APK. A new Kotlin action, Android permission declaration or unsupported OS capability requires an app update. The app never executes remote scripts, shell commands, JavaScript, DEX, JAR, APKs or downloaded libraries.

## Supabase API flow

1. register-device creates or updates a registered device and returns its server-issued ID.
2. get-my-repair-tools returns the currently published releases authorized for that member/device.
3. Android verifies each detached signature before writing an entry to Room.
4. Before execution, Android fetches the selected release again through get-signed-repair-tool; the server rechecks member, device, assignment, publication state and version.
5. The native engine validates every action ID and parameter locally, then runs only its compiled implementation.
6. submit-repair-run stores bounded execution telemetry. The app does not upload personal files, messages, photos or unrelated device data.

Use admin-panel/docs/ANDROID_API_CONTRACT.md for exact request and response shapes.

## Realtime and recovery

- Private WebSocket channel while Android is foregrounded.
- Topic is scoped to one authenticated member and one server-issued device UUID.
- Events carry only an opaque event ID, never a recipe or signature.
- On notification the app refetches its authorized catalogue and verifies signatures before replacing local state.
- App launch, foreground/resume, reconnect and periodic WorkManager sync recover missed events.
- A Realtime event is only a hint to refetch; it never authorizes catalogue access or execution.
- The latest branch migration policy explicitly rejects a device whose revoked_at is set.

Production requires migration 20261106120000_private_realtime_catalog_broadcast.sql and Supabase Realtime Allow public access disabled.

## Environment variables and secrets

### Android build

The root .env.example retains:

- SUPABASE_URL
- SUPABASE_ANON_KEY — public client key name retained for build compatibility
- RECIPE_SIGNING_PUBLIC_KEY_PEM — matching X.509 public PEM, safe to bundle in the APK

The checked-in public-key value is a placeholder. Production signed recipes cannot verify until a real matching key pair is configured.

### Admin Panel and Edge Functions

Admin Panel browser-safe variables:

- NEXT_PUBLIC_SUPABASE_URL
- NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
- NEXT_PUBLIC_APP_URL

Supabase automatically injects the reserved SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY values into Edge Functions. Do not create custom secrets using the SUPABASE_ prefix.

Custom Edge Function secrets:

- RECIPE_SIGNING_PRIVATE_KEY_PEM: manually add in Supabase Dashboard → Edge Function Secrets.
- ADMIN_ORIGIN: exact deployed HTTPS Admin Panel origin; the guarded production workflow syncs it from the protected GitHub environment variable.

## Deployment

Render preview: https://mahi-repair-admin-preview.onrender.com

The repository's render.yaml creates only the Free preview service. Production requires a separately created Render Web Service and a final HTTPS origin. The preview URL must not be described as the production Admin Panel.

The production Supabase workflow requires main, typed input DEPLOY and approval from the protected supabase-production GitHub environment. Required GitHub environment entries: SUPABASE_ACCESS_TOKEN, SUPABASE_DB_PASSWORD secrets and ADMIN_ORIGIN variable. The private signing key is created directly in Supabase, never in GitHub.

## Verification and release checklist

### Verified current-head CI

Branch security-hardening/run-repair, commit dd7c922bf5533c34a424d8d7994f29d2d0fb0f82:

- [Android CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251879): unit tests, debug APK build, API 35 Compose emulator tests, source/APK privileged-secret marker scans.
- [Supabase Edge Functions CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251882): type-checks all 15 function entrypoints and shared modules; runs canonical contract tests.
- [Admin Panel CI](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251933): dependency audit, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build.

[Debug APK artifact](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42167251879/artifacts/13479439548) is debug-signed with an ephemeral CI key. It is not a production release artifact.

### Local CI commands

Admin Panel:

    cd admin-panel
    npm ci
    npm run check:scaffold
    npm run typecheck
    npm run lint
    npm run test
    npm run build

Android:

    ./gradlew :app:testDebugUnitTest
    ./gradlew :app:assembleDebug
    ./gradlew :app:connectedDebugAndroidTest

Supabase Edge Functions and contract tests are checked by .github/workflows/supabase-functions-ci.yml using Deno.

### Production acceptance (not completed)

- Verify production Render HTTPS, owner authentication and owner-only routes.
- Provision matching RSA-3072 key pair; configure the private key only in Supabase and the public key only in Android release configuration.
- Configure protected Supabase deployment environment, Supabase Auth URL/redirects, owner profile, private Realtime and JWT-verified Edge Functions.
- Invite and authenticate a real member; register a physical device.
- Publish a recipe; verify database version commit, Realtime notification, catalogue sync and RSA signature.
- Execute supported native diagnostic actions; submit a report and verify it in the Admin Panel.
- Disable/unpublish the recipe; revoke the device; verify server-side denial.
- Test offline/reconnect, expiry/refresh rotation, missed/duplicate events, member suspension and multiple devices.
- Build and verify a release-signed APK/AAB; test installation and update on a physical Android device.
- Establish key rotation/backup, monitoring, backup/restore and privacy/retention procedures.

A passing CI workflow does not replace live production integration tests or physical-device acceptance. See admin-panel/docs/BUILD_STATUS.md and admin-panel/docs/PRODUCTION_READINESS.md for the verified state and release blockers.


## Latest candidate CI evidence — 2026-11-07

The release candidate `integration/mahi-repair-release-candidate` at code commit `3207acb104a1ee390ba0a9fa621ae234d5bcfd01` passed all four CI workflows:
- Android #42356155144: 6/6 Compose UI tests, unit tests, debug APK assembly, source/APK secret scans and artifact upload passed.
- Admin Panel #42356155163: production build passed.
- Supabase Edge Functions #42356155152: all candidate function entrypoints and contract tests passed.
- Supabase Database #42356155031: seven fresh migrations and 17 pgTAP checks passed.

The installable debug APK is [artifact #13567644352](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42356155144/artifacts/13567644352), extracted file SHA-256 `9e2a786b1683d6c4bfc6cb40adf85be9e4eb85586e8d3a31fa63dfe0f66e9aec`. This is a debug build and not a production release.

Production hosting is Cloud Run per `admin-panel/docs/DEPLOYMENT.md`. The legacy Render preview tracks an older branch and is not verified as production. Recipe signing, Auth email activation, leaked-password protection, release signing, real live device/catalogue population and physical-device acceptance remain outstanding.
