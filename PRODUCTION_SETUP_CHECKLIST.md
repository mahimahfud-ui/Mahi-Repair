# Mahi Repair — Live Production Handover

**Production inspection updated:** 20 November 2026

This is the operational checklist for the current shared-Supabase architecture. **Android target: public access with automatic anonymous Supabase Auth; no user email, PIN, Member ID, OTP, or invitation.** Read the status addendum below before using the older 11 November deployment observations.

## 0. Latest production inspection — 20 November 2026

Current source checkpoints:
- Android `main`: `88850e27621322f2c546ecaf71f7dd285f2ffdda` (the source commit before this documentation-only runbook commit).
- Separate Admin `main`: `2b1a354eeece55b0a495a139ad90309c02f75418`.
- Android CI, Backend CI, and Admin CI were successful on the recorded source checkpoint.

Production update-engine state from the latest inspection:
- The signed app-update migration is applied in the shared production Supabase project `shwqsljicmhflnvjqjah` (`ap-south-1`).
- Private Storage bucket `mahi-app-updates` exists with ZIP/APK restrictions and Owner-controlled upload paths.
- App-update Edge Functions are active: `admin-publish-app-update` (v2), `get-app-update-manifest` (v1), and `get-app-update-asset-url` (v1). The publish function validates stored assets, signs canonical manifests with the existing server-side RSA key, verifies the signature against the Android public key, then commits the immutable release.
- The app-update release table uses Owner-only read access and immutable release records. The Owner publish function is restricted to the service role; Member clients receive only authorized manifests and short-lived asset URLs.
- Last observed state: zero published app-update releases and zero registered devices. Recheck live counts before any rollout; do not publish a dummy release to test.
- Android `main` includes the signed update verifier, private asset download checks, system-installer handoff, data-package staging/activation and rollback. Update checks are wired after Member authentication, on app foreground, and on the device-scoped `app_update_published` Realtime event.

Production app / Admin state:
- Android is the public anonymous client; Owner actions remain in the separate Admin repository and Render website.
- The canonical Admin login URL is `https://mahi-repair-admin-yy7c.onrender.com/login`. Login UI and unauthenticated route protection were observed, but a successful Owner PIN session has not been accepted.
- Last observed profile state: one active Owner and one active Member. Real Member OTP delivery, device registration, repair execution and Admin report acceptance remain untested.
- The Supabase Security Advisor warning `auth_leaked_password_protection` remains open.

Production APK signing:
- No production APK/AAB or GitHub Release has been created.
- The previous manual workflow run `44760926078` failed closed because the three Android upload-signing secrets were missing; the build steps were skipped.
- A new permanent JKS upload keystore with alias `upload` has now been generated and its key-pair operation verified. Certificate SHA-256 (lowercase, no colons): `092cce4003935755b407f4670d50b84376c8e8f7495e0d81df679c7441b05636`.
- The keystore and one-time credentials handoff file are delivered separately to the Owner. They are not committed to this repository. The Owner must configure `ANDROID_UPLOAD_KEYSTORE_BASE64`, `ANDROID_UPLOAD_STORE_PASSWORD`, and `ANDROID_UPLOAD_KEY_PASSWORD` in GitHub Actions Secrets, then manually run the production workflow with confirmation `PUBLISH`.
- The Owner reported that Mahi Repair is not installed on their device. The first official install should come from the signed GitHub Release, not the root-level debug APK.

See [Production Release & Device Acceptance Runbook](docs/PRODUCTION_RELEASE_AND_DEVICE_ACCEPTANCE.md) for the exact release procedure and the real-device test sequence.

## 1. Canonical architecture

| Component | Canonical location |
|---|---|
| Public Android app | [Mahi-Repair repository](https://github.com/mahimahfud-ui/Mahi-Repair) |
| Separate Owner web console | [Mahi-Repair-Admin repository](https://github.com/mahimahfud-ui/Mahi-Repair-Admin) |
| Owner login | [Mahi Repair Admin](https://mahi-repair-admin-yy7c.onrender.com/login) |
| Shared Supabase project | `shwqsljicmhflnvjqjah` (`ap-south-1`) |

The Android APK is a public anonymous client. It does not contain an Owner Admin screen, route, Settings entry, or privileged Owner API client. Owner actions are available only through the separate Admin website.

Both clients use the same Supabase project and only its public publishable key. The service-role key and RSA recipe-signing private key remain server-side.

## 2. Verified deployment state

- Supabase reports `ACTIVE_HEALTHY`, PostgreSQL 17.
- Production migration history contains 9 applied migration entries.
- All 9 public tables have RLS enabled.
- All 17 production Edge Functions are `ACTIVE`. 16 require JWT verification; the Auth `send-email-hook` validates its signed webhook payload.
- Admin functions use the canonical Admin origin `https://mahi-repair-admin-yy7c.onrender.com`.
- Historical Render evidence recorded on 11 November reported the standalone Admin service as `LIVE`; its production build, TypeScript check, and server startup succeeded at that time.
- A live `/api/health` fetch on 11 November returned `status=configured` and `ready=true`; the login page also loaded. Recheck the current deployed SHA and endpoint in Render before release.
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

## 5. Public Android access — no email, PIN, or invitation

Before public launch:

1. In [Supabase Auth settings](https://supabase.com/dashboard/project/shwqsljicmhflnvjqjah/auth/providers), enable **Allow anonymous sign-ins**.
2. Keep Supabase Auth and Edge Function rate limits enabled. Add CAPTCHA/Turnstile protection where supported to limit automated anonymous-account creation.
3. Install the Android app and launch it. It must silently call Supabase anonymous sign-up and persist the resulting access/refresh tokens encrypted by Android Keystore.
4. Confirm the Auth database trigger creates an active `member` profile for the anonymous user. Existing Edge Functions then validate that profile and the device; the APK never receives the service-role key.
5. Register the device automatically. Confirm it appears in the separate Owner Console and can be revoked.
6. Publish an approved tool with audience `all_active_members`; it must appear for a fresh anonymous install. A `selected_members` tool must remain unavailable to anonymous public users.
7. Run only a non-destructive, allowlisted diagnostic tool. Confirm the signed recipe verifies locally and the report appears in the Owner Console.

No user email, OTP, Member ID, PIN, or invitation should be requested by Android. Anonymous identities are installation-scoped, not person-level identities; clearing app data or reinstalling can create a new identity.

## 6. Recipe signing and publication gate

- The Android build contains the RSA public verification key from the Android repository's `.env.example`.
- Supabase `admin-publish-tool` reads `RECIPE_SIGNING_PRIVATE_KEY_PEM` from Edge Function Secrets and signs canonical recipes using RSA-SHA256.
- The deployed private key's presence, PKCS#8 format, RSA-3072 size, and match to the Android public key have **not** been independently verified by the connected tools.

**Do not publish or assign a production repair tool until the Owner securely confirms the deployed private key matches the Android public key.** Never print, commit, or send the private key. Do not generate a new keypair as a shortcut; replacing one side without coordinating the other will invalidate signatures.

## 7. Full end-to-end public acceptance sequence

After Anonymous Sign-Ins and the recipe signing-key match are verified:

1. Owner signs in to the separate Admin website using the Owner PIN.
2. Owner creates a repair tool, validates its recipe in Test Lab, and checks its action list against the Android allowlist.
3. Owner publishes the tool with audience `all_active_members` for public use. Keep `selected_members` for deliberately restricted member-only releases.
4. Install the APK on a clean Android device and launch it. No login, PIN, email, OTP, Member ID, or invitation screen may appear.
5. Confirm a new anonymous profile and registered device are created automatically and shown in the Owner Console.
6. Confirm the app receives the published tool, verifies its RSA-SHA256 signature, and displays it in the library.
7. Run only a safe, non-destructive diagnostic or guided action approved for the allowlist.
8. Confirm the report appears in Admin under the anonymous profile/device.
9. Verify duplicate-run protection, tool disable/unpublish, device revocation, offline/reconnect, token refresh, native app update, signed data update, and rollback.
10. Verify a `selected_members` tool does not appear or execute for a public anonymous installation.

The last recorded production data inspection showed 2 profiles (1 Owner and 1 Member), 0 registered devices, 0 repair tools, 0 published versions, 0 repair runs, and 0 audit events. Recheck live counts and record physical-device acceptance; do not claim an unperformed test.

## 8. Android build and release signing

The normal CI artifact is a **debug APK**, about 24 MB, and is not the production release. A permanent production JKS keystore with alias `upload` has now been generated and locally verified; its certificate SHA-256 is `092cce4003935755b407f4670d50b84376c8e8f7495e0d81df679c7441b05636` (lowercase, no colons).

The Owner must add these GitHub Actions repository secrets:
- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_PASSWORD`

The keystore and credentials were delivered separately and are not present in source control. Retain secure backups; never replace this key. After adding the secrets, manually run **Actions → Android Production Release** from `main` with confirmation `PUBLISH`. A successful signed APK/AAB and matching certificate fingerprint have not yet been produced or verified.

## 9. Optional legacy backend / remote support

`MAHI_API_BASE_URL` and `REMOTE_SUPPORT_API_BASE_URL` in the Android build example are intentionally fail-closed placeholders. The separate legacy Express staging backend is not a production data service and must not be pointed at the Admin website. Keep optional device telemetry/remote-support API features disabled until a durable, authenticated backend and its full security tests are ready.

## 10. Release decision

**Not yet fully production-ready.**

Verified: separate repositories, shared healthy Supabase project, schema/RLS/migrations, active Edge Functions, canonical Admin deployment, Admin health readiness, Android CI/debug APK build, and no embedded Owner Admin UI in the APK.

Still required: enable Supabase Anonymous Sign-Ins, actual Owner PIN login in the separate Admin website, deployed RSA private/public-key match, stable production Android signing, and real-device anonymous first-launch → register → publish → verify → repair-run → Admin-report acceptance.
