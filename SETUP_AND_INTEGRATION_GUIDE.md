
# Mahi Repair — setup and integration guide

## Product layout

1. Android app: a single RUN REPAIR action on the home screen. Tapping it opens the currently published and authorized repair-tool picker.
2. Admin Panel: a separate private Next.js Owner console. It drafts, validates, tests, signs and publishes recipes, and manages invited members and registered devices.
3. Supabase backend: Auth, PostgreSQL/RLS, private Realtime Broadcast and Edge Functions.

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

The APK embeds only the matching X.509 public key. Keep the PKCS#8 private key only in Supabase Edge Function Secrets. Never put the private key in Android resources, GitHub, Render, Cloud Run or the browser.

## Existing native action allowlist and one-install behavior

The installed APK implements these 13 native actions:

CHECK_INTERNET, DNS_LOOKUP, HTTPS_PROBE, CHECK_APP_INSTALLED, CHECK_STORAGE, OPEN_APP, OPEN_APP_DETAILS, OPEN_ANDROID_SETTINGS, SHOW_GUIDE, ASK_USER_CONFIRMATION, REQUEST_OWN_APP_PERMISSION, VERIFY_INTERNET, VERIFY_DNS.

The Owner can publish additional recipes combining these actions without requiring users to reinstall the APK. A new Kotlin action, Android permission declaration or unsupported OS capability requires an app update. The app never executes remote scripts, shell commands, JavaScript, DEX, JAR, APKs or downloaded libraries.

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
- Device revocation is enforced by the server on subsequent online operations.

## Environment variables and secrets

### Android build

The root .env.example retains:
- SUPABASE_URL
- SUPABASE_ANON_KEY — public client key name retained for build compatibility
- RECIPE_SIGNING_PUBLIC_KEY_PEM — matching X.509 public PEM, safe to bundle in the APK

The checked-in public-key value is a placeholder. Production signed recipes cannot verify until a real matching keypair is configured.

### Admin Panel and Edge Functions

Admin Panel browser-safe variables:
- NEXT_PUBLIC_SUPABASE_URL
- NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
- NEXT_PUBLIC_APP_URL

Supabase automatically injects reserved runtime variables into Edge Functions. Do not create custom secrets using the SUPABASE_ prefix.

Custom Edge Function secrets:
- RECIPE_SIGNING_PRIVATE_KEY_PEM: add directly in Supabase Dashboard → Edge Function Secrets.
- ADMIN_ORIGIN: exact deployed HTTPS Admin Panel origin.
- SEND_EMAIL_HOOK_SECRET, RESEND_API_KEY, BREVO_API_KEY and AUTH_EMAIL_FROM: configure directly in Supabase Edge Function Secrets only after sender-domain setup and hook deployment preparation.

Never paste secret values into chat, browser-visible configuration or source control.

## Deployment

Production Admin hosting is Google Cloud Run. The current deployment guide is admin-panel/docs/DEPLOYMENT.md. Build/deploy from the admin-panel directory using its Dockerfile, then configure:
- NEXT_PUBLIC_SUPABASE_URL
- NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
- ADMIN_OWNER_EMAIL (server-only; never NEXT_PUBLIC_)
- Exact canonical Admin HTTPS origin, also configured as ADMIN_ORIGIN in Supabase Edge Function Secrets.

The Owner PIN is the existing Owner Supabase Auth password, validated server-side against the active Owner profile. It is not stored in the APK, browser storage or source code.

Legacy Render preview:
https://mahi-repair-admin-preview.onrender.com

That Render service still tracks security-hardening/run-repair and is an older preview deployment, not the current candidate or production Admin Panel. No Cloud Run production deployment or health check is verified.

The production Supabase workflow is manual and protected. Do not run it against main without explicit Owner approval, required protected environment values and a reviewed preview. Production migrations, functions and secrets are not changed by CI.

## Current candidate verification

Candidate branch: integration/mahi-repair-release-candidate
Current head: 65f31853db6c8c6d10d1299cefc564da12781b24

- Android CI #42359332992: Android unit tests and debug APK assembly passed; Compose emulator tests are currently running. Source/APK secret scans and artifact upload are pending.
- Admin Panel CI #42359333024: passed dependency/security checks, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build.
- Supabase Edge Functions CI #42359332985: passed all candidate Edge Function type checks and contract/source tests.
- Supabase Database CI #42359332983: all eight migrations applied to a fresh ephemeral local Supabase database; all 18 pgTAP checks passed.

Links:
- Android: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332992
- Admin: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359333024
- Edge Functions: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332985
- Database: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332983

Previous verified debug APK artifact #13567644352 is available at:
https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42356155144/artifacts/13567644352

It is a 24.5 MB debug-signed APK, SHA-256:
9e2a786b1683d6c4bfc6cb40adf85be9e4eb85586e8d3a31fa63dfe0f66e9aec

It is not a production release-signed APK/AAB. The previous full Android run #42356155144 passed 6/6 Compose tests. The current head is receiving a fresh Android run after the database-index changes and workflow path filter.

## Production acceptance (not completed)

- Verify Cloud Run HTTPS, health, Owner authentication and Owner-only routes.
- Provision a matching RSA-3072 key pair; configure private key only in Supabase and public key only in Android build configuration.
- Configure the protected Supabase deployment environment and exact Admin origin.
- Configure and test Resend/Brevo delivery, deploy the signed Auth email hook and activate the Supabase Auth hook only after inbox tests.
- Enable Supabase Auth leaked-password protection.
- Invite and authenticate a real Member; register a physical device.
- Publish a signed recipe and verify database version commit, private Realtime notification, catalogue sync and RSA signature.
- Execute supported native diagnostic actions; submit a report and verify it in the Admin Panel.
- Disable/unpublish the recipe; revoke the device; verify server-side denial.
- Test offline/reconnect, expiry/refresh rotation, missed/duplicate events, Member suspension and multiple devices.
- Build and verify a release-signed APK/AAB; test installation and update on a physical Android device.
- Establish signing-key rotation/backup, monitoring, backup/restore and privacy/retention procedures.

Passing CI does not replace live production integration tests or physical-device acceptance.
