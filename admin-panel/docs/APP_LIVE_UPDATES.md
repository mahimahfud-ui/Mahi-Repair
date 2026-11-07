# Mahi Repair signed live updates

## Release flow

The Owner publishes a release in **App Update Manager**. The Admin Panel uploads ZIP/APK assets to the private `mahi-app-updates` Storage bucket, computes a SHA-256 digest server-side and submits a typed manifest to the `admin-publish-app-update` Edge Function. The function rechecks the active Owner, rate-limits publication, verifies every uploaded object and size/MIME, canonicalizes the manifest and signs it with the existing RSA-3072/SHA-256 release key. The SQL RPC inserts the immutable release row and audit record in one transaction.

The database trigger broadcasts only an opaque `app_update_published` event to registered active member devices. Android must fetch the manifest from `get-app-update-manifest`, verify its detached RSA-SHA256 signature against the bundled public key and only then parse configuration. The asset endpoint checks the member and registered non-revoked device, confirms the asset belongs to the selected signed release, and returns a 90-second private Storage URL.

## Manifest contract

- `schemaVersion` is 1; channels are `stable` and `beta`.
- `releaseNumber` increases monotonically within each channel. Published rows are immutable.
- `remoteConfig` allows booleans, labels, tool metadata, announcement text and Android API compatibility ranges. There is no script, HTML, WebView, reflection or arbitrary command field.
- ZIP resources must be declared in `packages[].entries` with exact relative path, SHA-256 and uncompressed byte size. Only JSON, PNG, JPEG and WebP resources are accepted. A ZIP extractor must reject path traversal, undeclared entries, duplicate entries, over-limit expansion and hash/size mismatches before atomically activating a new directory.
- `nativeApk` metadata is optional. The APK must match the installed application ID, increase the version code and preserve signing-certificate continuity. The Android client must hand off to Android's system package installer and leave user confirmation / unknown-app installation permission under OS control.
- Signed package URLs are not embedded in the manifest and are not public. A client obtains a short-lived URL using `get-app-update-asset-url` only after device authorization.

## Rollback

Do not mutate or delete a published row. Publish the earlier remote configuration/package descriptor as a new higher `releaseNumber`, signed with the current release key. Native APK rollback is permitted only if Android accepts a higher versionCode built from the same signing identity; Android does not support downgrading an installed application through the normal package installer.

## Required production setup

1. Apply `20261107210000_app_update_releases.sql` to the intended Supabase environment.
2. Deploy `admin-publish-app-update`, `get-app-update-manifest` and `get-app-update-asset-url` Edge Functions.
3. Confirm the existing `RECIPE_SIGNING_PRIVATE_KEY_PEM` contains the RSA-3072 private key matching `RECIPE_SIGNING_PUBLIC_KEY_PEM` bundled in Android. Never upload the private key into the APK or Git.
4. Set the production app package ID and certificate SHA-256 accurately. The current Android Gradle applicationId in the repository is `com.aistudio.mahirepair.qrzntk`; change it only as a deliberate app identity migration.
5. Upload only packages produced from a trusted release pipeline. The upload API calculates the outer file digest; the manifest's inner-entry digests must come from the same deterministic package build.
6. Run signed-manifest verification and package-install tests on real Android hardware before making a mandatory release.

## Important boundary

The backend and Admin publication path do not by themselves update an installed Android app. The Android manifest verification, content-package download/extraction/atomic activation, native APK certificate verification and PackageInstaller UI must be implemented and device-tested before describing the whole live-update workflow as production-ready. A successful Edge Function or Admin build is not proof of that client lifecycle.
