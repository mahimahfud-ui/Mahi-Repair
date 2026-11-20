# Android production release signing

The first permanent production upload keystore for Mahi Repair has been generated because no previous release keystore was available and the Owner confirmed the app was not installed on their device.

## Permanent signing identity

- Keystore file: `MahiRepair-upload-release.jks` (delivered separately to the Owner)
- Store type: JKS
- Alias: `upload`
- Algorithm: RSA-3072 / SHA256withRSA
- Pinned certificate SHA-256: `092cce4003935755b407f4670d50b84376c8e8f7495e0d81df679c7441b05636` (lowercase hexadecimal, no colons)

This is the stable APK signing identity for the app. Keep secure backups and never generate a replacement for later releases. It is separate from the Supabase server-side key that signs app-update manifests and repair recipes.

## Required GitHub Actions repository secrets

At GitHub → Mahi-Repair → Settings → Secrets and variables → Actions, configure:

- `ANDROID_UPLOAD_KEYSTORE_BASE64` — Base64 of the delivered JKS file.
- `ANDROID_UPLOAD_STORE_PASSWORD` — the JKS store password.
- `ANDROID_UPLOAD_KEY_PASSWORD` — password for alias `upload`.

The values are provided in the separate private Owner handoff file. Do not commit the JKS, Base64 data, or passwords to source control, issues, PRs, chat, or logs.

## Release procedure

1. Add the three repository secrets.
2. Open **Actions → Android Production Release** on `main`.
3. Choose **Run workflow** and enter the exact confirmation `PUBLISH`.
4. Confirm the workflow verifies both the keystore certificate and the built APK signature against the pinned fingerprint above.
5. Confirm a signed APK, AAB, and SHA-256 checksum file are published in the permanent GitHub Release.

The workflow remains manual-only and fails closed when secrets are absent, the alias is missing, or the certificate fingerprint does not match. It never creates or substitutes a key.

## Current release status

The last release attempt stopped at the missing-secrets validation step. No production-signed APK/AAB or permanent GitHub Release exists yet. The Owner must configure the secrets and trigger the workflow.
