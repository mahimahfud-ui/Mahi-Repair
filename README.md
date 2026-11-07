# Mahi Repair — Native Android Companion

Mahi Repair is an invite-only Android diagnostic and guided-repair client connected to the private Mahi Repair Cloud Owner Console and a shared Supabase backend.

## Source of truth

- **Installable Android app:** native Kotlin + Jetpack Compose under `app/`.
- **Owner Admin Console:** separate repository, [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin), deployed at https://mahi-repair-admin.onrender.com.
- **Backend:** Supabase project `shwqsljicmhflnvjqjah`; Edge Functions enforce member/Owner authorization, device ownership, recipe publishing and run reporting.
- **Root `src/`:** older React/web prototype and action-engine reference. It is not the source used to assemble the Android APK.

## Android security and distribution

- The APK executes only native actions compiled into the app. Repair recipes are declarative JSON, never scripts or downloaded executable code.
- The app verifies the exact signed payload bytes using RSA-SHA256 / RSASSA-PKCS1-v1_5 and an **RSA-3072 public key** before parsing or execution.
- Only the matching public key belongs in Android build configuration. The RSA-3072 PKCS#8 private signing key must remain in Supabase Edge Function Secrets.
- Supabase Member sessions are encrypted with Android Keystore-backed AES-GCM storage.
- Each member device receives a server-issued device UUID. The Owner can revoke access.
- A fresh server-authorized signed recipe is fetched before every run; cached catalogue metadata alone never authorizes execution.
- The app supports a compiled allowlist of 13 actions: `CHECK_INTERNET`, `DNS_LOOKUP`, `HTTPS_PROBE`, `CHECK_APP_INSTALLED`, `CHECK_STORAGE`, `OPEN_APP`, `OPEN_APP_DETAILS`, `OPEN_ANDROID_SETTINGS`, `SHOW_GUIDE`, `ASK_USER_CONFIRMATION`, `REQUEST_OWN_APP_PERMISSION`, `VERIFY_INTERNET`, and `VERIFY_DNS`.

## Live tool delivery

1. An Owner creates a recipe in the Admin Console, validates it and tests it in Test Lab.
2. The publish Edge Function validates the v1 schema, canonicalizes the JSON and signs a new immutable version.
3. The Android client receives an opaque private Realtime event while foregrounded and fetches the authorized catalogue from Supabase.
4. The app verifies the signature and compatibility before displaying the new tool.
5. Each execution fetches the current signed release again and submits an idempotent run report.

The app keeps the private Realtime socket only while foregrounded. A 15-minute WorkManager catalogue refresh is a **best-effort fallback**; Android may defer it for battery and system scheduling. Background push notifications (FCM) are not yet configured. New website recipes can be delivered without an APK update when they use existing native action IDs. A new native capability or action ID requires an app update.

## Requirements

- Android API 26 or higher (minSdk 26).
- Internet access for invitation OTP, authorization, recipe sync and report upload.
- Matching RSA-3072 public key configured at build time.
- Keep the Android `applicationId` and release signing certificate stable for in-place app updates.

## Build and test

From the repository root:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

For a production release, configure the protected Android release keystore and build:

```bash
./gradlew :app:assembleRelease
```

The Android release keystore is separate from the RSA recipe-signing key. Never commit either private key, passwords, Supabase service-role keys or member credentials.

The root React prototype can be run separately with `npm install` and `npm run dev`; it does not build the native APK.
