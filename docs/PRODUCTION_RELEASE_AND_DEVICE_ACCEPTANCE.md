# Mahi Repair — Production Release & Device Acceptance Runbook

Updated: 20 November 2026

## Current verified source state

- Android `main`: `88850e27621322f2c546ecaf71f7dd285f2ffdda`
- Separate Admin `main`: `2b1a354eeece55b0a495a139ad90309c02f75418`
- Application ID: `com.aistudio.mahirepair.qrzntk`
- Current Android version: `versionCode = 1`, `versionName = 1.0`
- Existing production release workflow: `.github/workflows/android-release.yml`
- The previous production-release attempt failed closed at the missing Actions-secret validation step. No production APK/AAB or GitHub Release was created.
- The app-update Android client is wired to check after public anonymous session setup, when the app returns to the foreground, and after the device-scoped `app_update_published` Realtime event. The UI exposes check/install/data-apply/data-rollback actions.
- The live update engine was separately provisioned in Supabase: private `mahi-app-updates` storage bucket and `admin-publish-app-update`, `get-app-update-manifest`, `get-app-update-asset-url` functions. Last observed published-release and registered-device counts were zero. Recheck current production state before any rollout.
- No production update should be published merely to simulate a test.

## Permanent Android APK signing identity

A first production Android upload keystore has been generated for this app because the previous upload keystore was unavailable and the Owner confirmed the app was not installed on their device.

- Keystore file delivered separately: `MahiRepair-upload-release.jks`
- Keystore type: JKS
- Alias: `upload`
- Key: RSA-3072, SHA256withRSA
- Certificate SHA-256 (64 lowercase hex characters, no colons):
  `092cce4003935755b407f4670d50b84376c8e8f7495e0d81df679c7441b05636`

**This is the permanent Android APK signing identity.** Keep multiple private backups. Never regenerate or replace it. Never commit the JKS or the credentials file to GitHub. This Android APK signing key is different from the server-side RSA key used to sign app-update manifests and repair recipes.

### Configure GitHub Actions

At [Mahi-Repair Actions secrets](https://github.com/mahimahfud-ui/Mahi-Repair/settings/secrets/actions), create exactly these repository secrets using the separately delivered `MahiRepair-GitHub-Actions-Secrets.txt` file:

1. `ANDROID_UPLOAD_KEYSTORE_BASE64`
2. `ANDROID_UPLOAD_STORE_PASSWORD`
3. `ANDROID_UPLOAD_KEY_PASSWORD`

Do not put the keystore, Base64 value, or passwords in repository files, GitHub variables, issues, PRs, chat, or logs. After transferring the values into GitHub Secrets, securely delete the plaintext credentials file from shared/download folders while retaining an encrypted backup of the JKS and passwords.

Then open [Android Production Release](https://github.com/mahimahfud-ui/Mahi-Repair/actions/workflows/android-release.yml), choose **Run workflow** on `main`, and enter the exact confirmation `PUBLISH`. Confirm that:
- the existing `upload` alias is accepted;
- tests pass;
- a signed APK and AAB are produced;
- the APK certificate fingerprint equals the fingerprint above;
- SHA-256 checksums are attached;
- the permanent GitHub Release contains the APK and AAB.

Install only this production Release APK for first production acceptance. Do not treat the root-level `Mahi-Repair-Installable.apk` or an old debug CI artifact as the production release.

## Owner / public app / physical-device acceptance

This section requires a real Android device and a real Owner session. These steps cannot be marked complete by source-code inspection alone.

### 1. Owner console
- Open [Owner login](https://mahi-repair-admin-yy7c.onrender.com/login).
- Sign in with the existing Owner PIN. Do not share the PIN.
- Confirm the Owner dashboard loads and `/updates` is accessible only after Owner authentication.
- Confirm the production release manager loads and the private update bucket is available.
- No Owner authentication or administrative route may be embedded in the Android APK.

### 2. Public anonymous access and device registration
- In Supabase Dashboard → Authentication → Sign In / Providers, enable **Allow anonymous sign-ins**.
- Install the APK on a clean Android device and launch it.
- Confirm the app does not request email, OTP, Member ID, PIN, invitation, or manual registration.
- Confirm it silently creates a Supabase anonymous Auth user and stores session tokens encrypted by Android Keystore.
- Confirm the Auth trigger creates an active Member-profile row, the app registers its device, and the device appears in the separate Admin Console.
- Confirm the Owner can revoke the device and the same installation remains blocked after refresh/relaunch. Clearing app data/reinstalling creates a new anonymous identity and cannot be treated as a person-level identity.

### 3. Public tool publication and safe run
- Publish a validated tool with audience `all_active_members` from the Owner Console.
- Confirm the anonymous app receives the tool from Supabase and verifies its RSA-SHA256 signature.
- Confirm a `selected_members` tool stays unavailable to anonymous public users.
- Confirm `repair.run_enabled = false` disables Run Repair and `true` enables it only for a currently published, server-authorized recipe.
- Use only a non-destructive diagnostic/guided action explicitly approved and covered by the Android allowlist.
- Confirm the repair report appears in the Owner Console under the anonymous profile/device.
- Verify duplicate-run protection, tool disable/unpublish, and device revocation fail closed.

### 4. Native app update
A native update must be a real, newer release. The currently configured app is version code 1; an update APK must have a higher version code and use the permanent upload key above.

Before publishing:
- build the newer APK from trusted `main`;
- confirm package name `com.aistudio.mahirepair.qrzntk`;
- compute APK SHA-256 and exact byte size;
- set `signingCertificateSha256` to the 64-character fingerprint above (lowercase, no colons);
- upload to the private stable/beta path through the authenticated Owner release manager;
- confirm server-side asset verification and manifest self-signature validation succeed.

On the device, tap **Check for updates**, then **Download & install APK**. Confirm the app verifies the downloaded bytes and signing certificate, opens Android's system installer, and installs only after the user approves. Do not mark this test passed until the installed version code is newer and the public anonymous session/device registration still work.

### 5. Signed data update and rollback
- Use a narrowly scoped, non-destructive configuration or content change approved by the Owner.
- Publish only after reviewing the exact release manifest and target channel.
- Confirm the device receives the Realtime event (or detects the release on foreground/check), fetches the manifest, and verifies its RSA signature.
- Apply the signed data update; confirm the new declared configuration/resource is active.
- Tap **Rollback to previous data release** and confirm the previous data/configuration is restored.
- Do not use a native APK downgrade as a rollback test; this control is for the signed data/content release.

### 6. Final evidence
Record the exact APK Release URL, release tag, workflow run URL, APK SHA-256, signing certificate fingerprint, device model/Android version, Owner PIN result (Admin only), anonymous public-session result, registered device ID (do not expose it publicly), repair test outcome, native update result, data update result, and rollback result. Redact emails, OTPs, PINs, access tokens, and device identifiers from any public report.

## Current acceptance status

- Source and CI: passed on the recorded main commits.
- Production APK signing: keystore generated and locally verified; **GitHub secrets still need Owner configuration**.
- Production GitHub Release: pending successful manual workflow run.
- Owner PIN login: not yet accepted (Admin Console only; unrelated to Android public access).
- Public anonymous first-launch session/device registration: not yet accepted.
- Physical repair run: not yet accepted.
- Native APK update: not yet tested on a physical device.
- Signed data update/rollback: source-wired, not yet tested on a physical device.

**Release verdict: code and signing material prepared; Owner configuration and real-device acceptance remain mandatory.**
