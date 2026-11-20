# Current Build and Integration Boundaries

**Last audited: 20 November 2026.** This file describes the current Android source and its production acceptance boundaries. The standalone Owner Console lives in the separate [Mahi-Repair-Admin repository](https://github.com/mahimahfud-ui/Mahi-Repair-Admin).

## Product architecture

- `app/` is the native Kotlin/Jetpack Compose Android Member application.
- The Owner Admin website is a separate Next.js application in the separate repository above. It is not part of the Android user experience.
- The root Vite/React and Express files and the legacy `admin-panel/` subtree are not the production Android Member backend. Do not deploy their local JSON/demo backend as production storage.
- Android Auth, device registration, recipe sync, repair-run reporting and private Realtime use the shared Supabase project `shwqsljicmhflnvjqjah`.
- Firebase metadata/dependencies remain in the repository, but they do not establish that native Firebase Auth or Firestore is the active production runtime. Do not provision a parallel backend or run a Firebase migration based only on those files.

## Android build behavior

- Android CI runs unit tests and assembles a debug APK after tests pass.
- CI creates an ephemeral debug keystore. The resulting APK is for development/testing and is not a production-signed release.
- A separate manual GitHub Actions workflow builds the production APK/AAB using the existing upload keystore supplied through protected secrets.
- Preserve application ID `com.aistudio.mahirepair.qrzntk`, versioning discipline and the existing upload signing identity. Do not generate a replacement upload key or change the package ID.
- A successful build proves compilation/tests for that commit; it does not prove production credentials, deployed secrets, email delivery, device registration or physical-device repair behavior.

## Active Supabase runtime

The native client uses:

- `app/src/main/java/com/example/data/remote/SupabaseClient.kt` for OTP, session lifecycle, device registration, authorized catalogue, signed recipe and report APIs.
- `app/src/main/java/com/example/data/repository/ToolRepository.kt` for device identity, signed release validation, catalogue synchronization and outbox report upload.
- `app/src/main/java/com/example/sync/SupabaseRealtimeClient.kt` for private invalidation broadcasts.
- `app/src/main/java/com/example/workers/ToolSyncWorker.kt` and `ReportUploadWorker.kt` for background sync and durable report retry.
- `app/src/main/java/com/example/security/` for signature and recipe-contract verification.

Android must fetch a fresh server-authorized signed release before execution when current Member/device revocation must take effect. It executes only native, locally allowlisted action IDs, never downloaded scripts or arbitrary shell commands.

## Optional APIs intentionally fail closed

- `DeviceIntelligenceApiClient` and `RemoteSupportService` use the legacy `MAHI_API_BASE_URL` configuration.
- The checked-in example value is an invalid placeholder so these optional network features fail closed until a real authenticated durable service is delivered.
- Do not point these clients at the Owner website.
- The existing Remote Support service source does not prove working live video transport; a real WebRTC/media pipeline, visible Member approval, Android MediaProjection consent, audit and stop/revoke flow are still required.

## Production gates

Before a production release:

1. Confirm canonical standalone Admin URL, Render deployment SHA and Supabase Auth redirect configuration.
2. Test Owner PIN login and Member denial on the real website.
3. Verify Auth Send Email Hook wiring and actual invited Member OTP delivery.
4. Prove the production RSA private/public signing-key match without exposing private material.
5. Complete a real Android Member OTP → device registration → signed tool sync → safe action → report visible in Admin Logs flow.
6. Test revocation, offline report retry/idempotency, token refresh, Realtime and update handling on physical hardware.
7. Build and install the production-signed APK/AAB with the existing signing identity.

See the root [PRODUCTION_SETUP_CHECKLIST.md](../PRODUCTION_SETUP_CHECKLIST.md) and the standalone Admin [current production handover](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/CURRENT_PRODUCTION_HANDOVER.md).

## Live application updates

The signed App Update Engine exists in an unmerged Android integration branch, not in the current `main` production runtime. Its database migration, private Storage bucket and update Edge Functions were not present in production during the 20 November audit. Review and port the implementation as a separate staged change; do not merge stacked draft PRs or publish mandatory updates before physical-device acceptance.
