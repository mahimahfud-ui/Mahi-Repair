# Current Build and Release Requirements

This repository currently contains a native Kotlin/Jetpack Compose Android application under `app/`, a legacy root Vite web prototype, an embedded Admin panel, and a Node.js backend prototype.

## Build behavior

- Android CI runs unit tests and produces a debug APK only when all tests pass.
- CI creates an ephemeral debug keystore. Its APK is for testing and is not a production-signed release.
- The root npm lockfile is synchronized from the root `package.json` on the feature-branch CI path before a clean install.
- The embedded Admin panel has its own lockfile and independent CI checks.

## Runtime configuration

The Android build can compile with the explicit placeholder values in `.env.example`. Those values are intentionally unusable. A successful build does not mean any backend is configured.

Before a real release, configure:
- A real Supabase URL and publishable/anon key for the current Android Supabase client.
- A matching RSA-3072 signing private/public keypair. Keep the private PEM only in server-side secrets and embed only the matching public PEM in Android.
- A real HTTPS remote-support API base URL pointing to a deployed service that implements the documented API.
- The Owner account, roles, email delivery and server-side PIN configuration.

## Current integration boundaries

- The Android authentication, catalogue and foreground Realtime implementation still uses Supabase. Firebase project metadata in the repository is not, by itself, a native Firebase Auth/Firestore integration.
- The root Express backend currently uses a local JSON staging database abstraction. It must be replaced with durable shared storage before multi-instance production deployment.
- Remote diagnostic commands are restricted to signed, allowlisted read-only operations. Guided actions requiring foreground user confirmation are not dispatched by the background service.
- The Android remote-support service does not currently implement a WebRTC screen capture/encoding/peer-connection pipeline. Its presence does not prove live laptop screen sharing.
- Device intelligence collection is present, but uploading that profile to the currently configured Android backend still requires a completed repository integration.
- Render Admin remains a separate Next.js application using its existing Supabase integration. Its production deployment has not been migrated by this build-stabilization branch.

A test APK must not be described as production-ready until real credentials, matching signing keys, durable storage, deployed API routes and physical-device acceptance tests have been verified.
