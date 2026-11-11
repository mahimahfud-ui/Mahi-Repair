# Mahi Repair — Android Member App

Mahi Repair is the native Android repair client for invited Members. The Android app contains only the Member experience: sign-in, the authorized repair library, guided repair execution, device status, and local repair history.

## Separate Owner Console

Owner management is provided only by the standalone [Mahi Repair Admin repository](https://github.com/mahimahfud-ui/Mahi-Repair-Admin), hosted at:

https://mahi-repair-admin.onrender.com

The Owner Console is a separate web application and is not embedded in this Android app. The Android client has no Owner Admin screen, route, Settings entry, or privileged Admin API client.

## Shared Supabase backend

Both applications use the same Supabase project:

- Project ref: `shwqsljicmhflnvjqjah`
- Region: `ap-south-1`
- Android: Member Auth, device registration, signed tool retrieval, and idempotent repair reports.
- Admin website: Owner-only member/device management, tool authoring, validation, publishing, and audit views.

The Android client uses only the public Supabase publishable key. The service-role/secret key and RSA recipe-signing private key must remain server-side in Supabase secrets. The APK embeds only the RSA public key used to verify signed recipes.

## Android configuration

Copy `.env.example` to `.env` for a local build. Never place Supabase service-role keys, Edge Function secrets, Owner PINs, or private signing material in this file or in the APK.

## Build and verification

Use the checked-in Gradle wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is for development/testing. A production release must use the owner's protected release-signing configuration and pass the physical-device acceptance gates documented in `PRODUCTION_SETUP_CHECKLIST.md`.

## Release boundary

A successful CI build does not prove live end-to-end operation. Before declaring production acceptance, verify the Owner PIN login, Member invitation/OTP delivery, RSA public/private key match, a real Member device registration, Owner tool publish, Android signature verification, safe repair execution, and report visibility in the Admin Console.
