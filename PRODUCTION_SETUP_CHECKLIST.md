# Mahi Repair — Production Setup and Handover Checklist

This checklist applies to the native Android client in this repository and the separate Owner Console at https://mahi-repair-admin.onrender.com. The production Supabase project is `shwqsljicmhflnvjqjah`.

## 1. Repository and deployment boundaries

- Android source of truth: this repository's `app/` Kotlin/Jetpack Compose module.
- Owner Console and Edge Functions source of truth: [mahimahfud-ui/Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin).
- Apply migrations and deploy Edge Functions from the **Mahi-Repair-Admin repository root**, not this Android repository's legacy `admin-panel/` copy.
- Never put a Supabase service-role key, Owner PIN or recipe-signing private key in Android config, browser code or Git.

## 2. Current production snapshot

Verified on 2026-11-07:
- Supabase project status: `ACTIVE_HEALTHY`.
- Seven migrations from the current Admin repository are applied.
- All 15 existing Member/Owner Edge Functions are active with JWT verification enabled.
- Owner Admin Render service is deployed from `main` and reports `live`.
- Owner server-only environment identifier has been configured in Render.
- These checks do **not** prove the RSA key is configured, the email provider works, the Owner can sign in or a physical Android device can complete a repair run.

## 3. Supabase database and Edge Functions

Run commands from the root of the Admin repository after checking the target project:

```bash
supabase login
supabase link --project-ref shwqsljicmhflnvjqjah
supabase migration list
supabase db push
```

Use the migration files under `Mahi-Repair-Admin/supabase/migrations/`. Review the planned migration list before applying any new migration to production.

Deploy the 15 Member/Owner functions with JWT verification enabled:

```bash
supabase functions deploy admin-assign-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-delete-draft-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-disable-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-invite-member --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-manage-device --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-manage-member --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-manage-tool-state --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-publish-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-save-validation --project-ref shwqsljicmhflnvjqjah
supabase functions deploy admin-upsert-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy get-admin-analytics --project-ref shwqsljicmhflnvjqjah
supabase functions deploy get-my-repair-tools --project-ref shwqsljicmhflnvjqjah
supabase functions deploy get-signed-repair-tool --project-ref shwqsljicmhflnvjqjah
supabase functions deploy register-device --project-ref shwqsljicmhflnvjqjah
supabase functions deploy submit-repair-run --project-ref shwqsljicmhflnvjqjah
```

Confirm every Member/Owner function is `ACTIVE` and `verify_jwt=true`. Do not deploy unreviewed migrations or disable JWT verification on these endpoints.

## 4. Recipe-signing key setup

Generate a dedicated RSA-3072 key pair. This key signs repair recipe releases; it is **not** the Android APK release keystore.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private_key.pem
openssl pkey -in private_key.pem -pubout -out public_key.pem
```

- Store the complete PKCS#8 private PEM as Supabase Edge Function Secret `RECIPE_SIGNING_PRIVATE_KEY_PEM`.
- Configure the matching X.509 public PEM as Android build property `RECIPE_SIGNING_PUBLIC_KEY_PEM`.
- Verify the public key modulus is 3072 bits and compare the public key derived from the private key before releasing.
- Keep `private_key.pem` outside Git and outside the APK. Remove temporary working copies only after backing up the key securely.
- A changed signing key requires a deliberate key-rotation/release plan; do not silently replace the public key in an already installed app.

## 5. Admin origin and Owner sign-in

Set Supabase Edge Function Secret:

```bash
supabase secrets set ADMIN_ORIGIN=https://mahi-repair-admin.onrender.com
```

The value must be the exact HTTPS origin, without a path or trailing slash. Confirm Supabase Auth Site URL is `https://mahi-repair-admin.onrender.com` and add the exact callback `https://mahi-repair-admin.onrender.com/auth/callback` to the redirect allowlist.

The production Owner Console uses an **8–12 digit numeric PIN** as the existing Owner Supabase Auth account password. It does not use an Owner Magic Link/OTP login flow. Keep `ADMIN_OWNER_EMAIL` server-only in Render, verify the matching profile has `role='owner'` and `status='active'`, and set/confirm the PIN through Supabase Auth administration. Do not commit or share the PIN.

## 6. Member email delivery

Member onboarding is separate from Owner PIN sign-in:
- Owner invites a Member from the Admin Console.
- The invited Member enters the invitation email in the Android app and verifies the six-digit Supabase Auth OTP.
- A successful sign-in registers a server-issued device ID and synchronizes authorized tools.

Supabase Auth must have a working email delivery provider. The separate Admin PR adding the Send Email Auth Hook implements Resend primary and Brevo fallback, but it must be reviewed/merged before deployment. Before enabling that hook:
- Configure `SEND_EMAIL_HOOK_SECRET`, `RESEND_API_KEY`, `BREVO_API_KEY` and `AUTH_EMAIL_FROM` in Supabase Edge Function Secrets.
- Verify the sending domain at both providers.
- Deploy `send-auth-email` with `--no-verify-jwt` **only** for this HMAC-verified Auth Hook endpoint.
- Configure Authentication → Hooks → Send Email with the exact function URL and the same Hook secret.
- Keep the hook disabled until real invitation and Member OTP inbox tests pass.

Do not disable JWT verification for any other Edge Function.

## 7. Android build configuration

The current app has:
- `applicationId = com.aistudio.mahirepair.qrzntk`
- `minSdk = 26`
- `targetSdk = 36`

Configure only public client values and the recipe-signing public key in the Android build environment:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` (the Supabase publishable/anon client key)
- `RECIPE_SIGNING_PUBLIC_KEY_PEM` (matching RSA-3072 public PEM)

For production updates, preserve the same `applicationId` and use the original protected Android release keystore. A debug APK signed by a temporary debug key is for CI/testing only, not a production update package.

## 8. End-to-end acceptance test

Use a dedicated test Member and a physical Android device. Verify in this order:

1. Owner signs into the production Admin Console with the configured PIN.
2. Owner invites a test Member; the invitation email is delivered.
3. Member receives and verifies the six-digit OTP in Android.
4. Android registers a new server-issued device ID; Admin shows that device.
5. Owner creates a recipe from existing allowlisted actions, validates it, runs Test Lab, then publishes it.
6. Foreground Android app receives the private Realtime invalidation event and fetches the catalog.
7. Android verifies the exact signed payload and current authorization before execution.
8. Run a safe read-only diagnostic such as `CHECK_INTERNET` or `DNS_LOOKUP`.
9. Android queues and submits the run report; Admin displays the matching run.
10. Replay the same `run_id` and verify the server treats it as an idempotent duplicate.
11. Revoke the test device and verify the next fresh catalog/recipe authorization is rejected.
12. Test offline then reconnect; pending report uploads retry and the catalog refreshes.

## 9. Release commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Before release, verify CI, test on at least one physical device, verify the release APK signature and preserve the existing package ID/signing certificate. This checklist is not complete until all production configuration and physical-device acceptance tests are recorded.
