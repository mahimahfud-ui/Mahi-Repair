# Mahi Repair — Public Android Repair App

Mahi Repair is a public native Android repair app. Anyone can install and open it; the app creates its own anonymous Supabase session in the background. Users do not need an email address, Member ID, PIN, invitation, or account-registration form.

## Public tool access

- The Android app automatically creates one anonymous Auth identity per app installation.
- The app downloads only Owner-published, RSA-signed repair recipes and verifies each signature before use.
- Tools published for **all active members** are available to public app users. Tools assigned only to selected members remain restricted.
- Device registration, server-side authorization, rate limits, recipe allowlists, safe execution prompts, and run-report validation remain active.
- A revoked public device remains blocked while its local app identity is retained. Anonymous access is not a person-level identity; clearing app data or reinstalling can create a new anonymous identity.

## Separate Owner Console

Owner management is provided only by the standalone [Mahi Repair Admin repository](https://github.com/mahimahfud-ui/Mahi-Repair-Admin), hosted at:

https://mahi-repair-admin-yy7c.onrender.com/login

The Owner Console is a separate web application. It is not embedded in the Android app and remains protected by Owner authentication. There is no Admin screen, route, button, or privileged Owner API client in the Android app.

## Shared Supabase backend

Both applications use the same Supabase project:

- Project ref: `shwqsljicmhflnvjqjah`
- Region: `ap-south-1`
- Android: anonymous Auth session, device registration, signed public tool retrieval, and repair-run reports.
- Admin website: Owner-only member/device management, tool authoring, validation, publishing, and audit views.

The Android client uses only the public Supabase publishable key. The service-role/secret key and RSA recipe-signing private key remain server-side in Supabase secrets. The APK embeds only the RSA public key used to verify signed recipes.

## Required Supabase Auth setting

Before public access works in production, enable **Allow anonymous sign-ins** in Supabase Dashboard → Authentication → Sign In / Providers → Anonymous. Keep Auth and Edge Function rate limits enabled. Consider CAPTCHA/Turnstile protection against automated account creation.

## Build and verification

Use the checked-in Gradle wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is for development/testing. A production release must use the Owner's protected, permanent release-signing configuration and pass the physical-device acceptance gates in `PRODUCTION_SETUP_CHECKLIST.md`.

## Get the APK from GitHub

Follow the [GitHub APK download and production release guide](docs/GITHUB_APK_RELEASE.md) to download a temporary CI test APK or create a permanent signed GitHub Release.

## Release boundary

A successful CI build does not prove live operation. Before production acceptance, verify Supabase anonymous sign-in, public all-active-members tool publication, selected-member tool isolation, device registration/revocation, signature verification, a safe repair run, Admin report visibility, app updates, and data-update rollback.
