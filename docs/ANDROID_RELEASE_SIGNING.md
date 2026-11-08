# Android production release signing

The normal Android CI job intentionally creates a **debug-signed** APK. This manual workflow builds a production-signed APK and AAB only with the app's existing upload keystore.

## Required GitHub Actions repository secrets

Configure these in **GitHub → Mahi-Repair → Settings → Secrets and variables → Actions**:

- `ANDROID_UPLOAD_KEYSTORE_BASE64` — Base64 of the existing `.jks`/`.keystore` file.
- `ANDROID_UPLOAD_STORE_PASSWORD` — keystore password.
- `ANDROID_UPLOAD_KEY_PASSWORD` — password for the existing `upload` alias.

Do not create a replacement keystore for an app already distributed. The release Gradle configuration currently expects alias `upload`; the workflow validates that alias before building.

## Run

Open **Actions → Android Production Release → Run workflow** on the trusted `main` branch. A successful run publishes artifact `mahi-repair-production-release` containing both APK and AAB.

If a secret is missing, the workflow stops before Gradle release signing. Never put keystore bytes or passwords in source code, `.env.example`, issues, pull requests, or chat messages.

## Current release status

This workflow is only the release pipeline. It does not prove that signing is configured until the existing keystore secrets are supplied and a successful signed release artifact is produced.
