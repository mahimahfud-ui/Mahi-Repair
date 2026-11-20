# Downloading Mahi Repair from GitHub

Mahi Repair APK builds are produced by GitHub Actions. Google AI Studio is not part of this build or backend workflow.

## Temporary test APK

1. Open the repository's [Actions page](https://github.com/mahimahfud-ui/Mahi-Repair/actions).
2. Open a successful **Android CI** run on the branch/commit you intend to test.
3. In **Artifacts**, download `mahi-repair-debug-apk`.
4. Extract the ZIP and install the APK on an Android test device.

This artifact is debug-signed with an ephemeral CI debug key and expires after 14 days. It is for testing only. It is not the production distribution APK and must not be used as the signing identity for app upgrades.

## Permanent signed APK release

The **Android Production Release** workflow creates a permanent GitHub Release containing:

- Signed APK for direct Android installation.
- Signed Android App Bundle (AAB) for supported app-store distribution.
- `SHA256SUMS.txt` with hashes for both files.

Before running it, confirm the existing upload-key secrets are configured in **Settings → Secrets and variables → Actions**:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_PASSWORD`

Do not paste secret values into source code, workflow inputs, issues or chat. Do not generate a replacement upload key for an app that has already been distributed.

To publish:

1. Merge the reviewed production-release workflow into `main`.
2. Open **Actions → Android Production Release**.
3. Select `main`, choose **Run workflow**, and type exactly `PUBLISH`.
4. Wait for unit tests, signed APK/AAB builds and GitHub Release upload to pass.
5. Open [GitHub Releases](https://github.com/mahimahfud-ui/Mahi-Repair/releases) and download the APK asset.

The production workflow is manual-only, requires the existing upload keystore and will fail if the current `upload` alias cannot be verified. Normal pushes and pull requests never publish a Release.

## Important signing distinction

An Android update can be installed over an existing app only when Android accepts the signing identity and package/version rules. A temporary debug APK uses a different, disposable CI key and may not update an installed production-signed app. Do not uninstall an existing production app merely to install a debug build; uninstalling can remove local app data.
