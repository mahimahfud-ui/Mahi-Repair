# Mahi Repair — Live Production Handover

**Last verified:** 11 November 2026

This document replaces the older setup guide. The production Supabase database and Edge Functions are already provisioned. Do **not** run migrations, recreate keys, or redeploy every function just to complete this handover.

## 1. Canonical architecture

| Component | Canonical location |
|---|---|
| Android Member app | [Mahi-Repair repository](https://github.com/mahimahfud-ui/Mahi-Repair) |
| Separate Owner web console | [Mahi-Repair-Admin repository](https://github.com/mahimahfud-ui/Mahi-Repair-Admin) |
| Owner login | [Mahi Repair Admin](https://mahi-repair-admin-yy7c.onrender.com/login) |
| Shared Supabase project | `shwqsljicmhflnvjqjah` (`ap-south-1`) |

The Android APK is a Member-only client. It does not contain an Owner Admin screen, route, Settings entry, or privileged Owner API client. Owner actions are available only through the separate Admin website.

Both clients use the same Supabase project and only its public publishable key. The service-role key and RSA recipe-signing private key remain server-side.

## 2. Verified deployment state

- Supabase reports `ACTIVE_HEALTHY`, PostgreSQL 17.
- Production migration history contains 9 applied migration entries.
- All 9 public tables have RLS enabled.
- All 17 production Edge Functions are `ACTIVE`. 16 require JWT verification; the Auth `send-email-hook` validates its signed webhook payload.
- Admin functions use the canonical Admin origin `https://mahi-repair-admin-yy7c.onrender.com`.
- Render reports the standalone Admin service as `LIVE`. Its production build, TypeScript check, and server startup succeeded.
- A live `/api/health` fetch returned `status=configured` and `ready=true`; the login page also loaded.
- Android PR #27 is merged to `main`. Android CI, Backend CI, and legacy scaffold CI passed. A 24 MB debug APK artifact was produced.

This verifies deployments and configuration shape, not the complete owner/member/device/repair workflow.

## 3. One required Supabase Auth URL setting

The generated Render hostname differs from the old reserved hostname. Open [Supabase Auth URL Configuration](https://supabase.com/dashboard/project/shwqsljicmhflnvjqjah/auth/url-configuration) and confirm:

**Site URL**
```
https://mahi-repair-admin-yy7c.onrender.com
```

**Additional Redirect URLs**
```
https://mahi-repair-admin-yy7c.onrender.com/**
```

The repository's `supabase/config.toml` records these canonical values, but this connected workflow cannot read or change the live Auth URL configuration. Verify the settings in the dashboard before sending real Member invitations.

## 4. Owner PIN login

1. Open [Owner Login](https://mahi-repair-admin-yy7c.onrender.com/login).
2. Enter the existing Owner's 8–12 digit numeric PIN.
3. The server uses the server-only `ADMIN_OWNER_EMAIL` environment value to sign in to the existing Supabase Auth Owner identity.
4. The server additionally checks that the authenticated user's profile has `role = 'owner'` and `status = 'active'`.

The Owner login page does not request an email, OTP, or Magic Link. The PIN is not stored in this repository or in the Android app. Do not send the PIN in chat or add it to `.env`.

The live health endpoint confirms that the Owner email setting is present and syntactically valid; it does **not** prove that the PIN is correct or that a login session has been created. Complete one normal Owner login test in the browser.

## 5. Member invitation and Android sign-in

Before inviting a real Member:

1. Confirm the Auth Site URL and redirect allowlist in section 3.
2. Confirm the Supabase Auth Send Email Hook is configured to call the deployed `send-email-hook` function with the matching signed webhook secret.
3. Confirm at least one approved email provider is configured in Edge Function Secrets (Resend or Brevo), with a verified sender/domain. Do not place provider credentials in GitHub source or browser-visible variables.
4. Invite one consenting test Member from the Admin website.
5. On Android, sign in with that invited Member's email and verify the Supabase email OTP.
6. Confirm the device is registered under that Member in the Admin website.

The Send Email Hook function is deployed and active, but successful Member email delivery has not yet been proven. Do not treat the presence of the function as proof that provider secrets, Auth Hook wiring, sender-domain verification, or email delivery work.

## 6. Recipe signing and publication gate

- The Android build contains the RSA public verification key from the Android repository's `.env.example`.
- Supabase `admin-publish-tool` reads `RECIPE_SIGNING_PRIVATE_KEY_PEM` from Edge Function Secrets and signs canonical recipes using RSA-SHA256.
- The deployed private key's presence, PKCS#8 format, RSA-3072 size, and match to the Android public key have **not** been independently verified by the connected tools.

**Do not publish or assign a production repair tool until the Owner securely confirms the deployed private key matches the Android public key.** Never print, commit, or send the private key. Do not generate a new keypair as a shortcut; replacing one side without coordinating the other will invalidate signatures.

## 7. Full end-to-end acceptance sequence

After sections 3–6 are verified:

1. Owner signs in to the Admin website using the PIN.
2. Owner invites one test Member; the Member receives and completes the invitation flow.
3. Member signs in to the Android app using email OTP.
4. Android registers its real device ID; Admin shows it under the correct Member.
5. Owner creates a repair tool, validates the recipe in Test Lab, and checks its action list against the Android allowlist.
6. Only after the signing-key match is proven, Owner publishes and assigns the tool.
7. Android syncs the published signed recipe and verifies the signature locally.
8. Run only a safe, supported diagnostic or guided action on the real device.
9. Complete the run and confirm the report appears in Admin.
10. Verify idempotent retry, disable/unpublish, device revocation, offline/reconnect, token refresh, and private Realtime behavior.

Current production data has 2 profiles (1 active Owner and 1 active Member) but 0 registered devices, 0 repair tools, 0 published versions, 0 repair runs, and 0 audit events. No physical-device end-to-end acceptance has been recorded.

## 8. Android build and release signing

The CI artifact is a **debug APK**, about 24 MB, signed with an ephemeral CI debug keystore. It is suitable for initial installation/testing, not a production release. A future CI run may use a different debug keystore, so an installed debug build may need to be uninstalled before installing another debug artifact.

A production release requires the Owner's stable release keystore and protected build variables:
- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

Do not commit the keystore or passwords. Use protected GitHub Actions secrets or the approved release environment. No production release-signed APK/AAB has been produced or verified yet.

## 9. Optional legacy backend / remote support

`MAHI_API_BASE_URL` and `REMOTE_SUPPORT_API_BASE_URL` in the Android build example are intentionally fail-closed placeholders. The separate legacy Express staging backend is not a production data service and must not be pointed at the Admin website. Keep optional device telemetry/remote-support API features disabled until a durable, authenticated backend and its full security tests are ready.

## 10. Release decision

**Not yet fully production-ready.**

Verified: separate repositories, shared healthy Supabase project, schema/RLS/migrations, active Edge Functions, canonical Admin deployment, Admin health readiness, Android CI/debug APK build, and no embedded Owner Admin UI in the APK.

Still required: live Supabase Auth redirect allowlist confirmation, actual Owner PIN login, real Member email delivery, deployed RSA private/public-key match, stable production Android signing, and the real-device invite → register → publish → verify → repair-run → Admin-report acceptance flow.
