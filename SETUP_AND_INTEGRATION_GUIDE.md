# Mahi Repair — Native Android Integration Guide

## 1. System boundaries

- The installable app is the Kotlin/Jetpack Compose module under `app/`.
- The Owner web console and all privileged backend operations live in [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin).
- Both clients use the Supabase project `shwqsljicmhflnvjqjah`.
- Root `src/` is a legacy React/web prototype; it is not the source used for the APK.
- Mahi Repair uses declarative, signed recipes and a fixed local action allowlist. It never downloads or executes remote shell scripts, JavaScript, DEX, JAR, APKs or arbitrary libraries.

## 2. Authentication and device identity

Android Members are invited by the Owner and use Supabase six-digit email OTP:

1. Member enters the invited email address.
2. Android requests an OTP with account creation disabled.
3. Member verifies the six-digit code.
4. Access and refresh tokens are stored encrypted with an Android Keystore AES-GCM key.
5. Android registers with the backend and persists the server-issued device UUID in app-private DataStore.

The Owner signs into the production Admin Console using the existing Owner Supabase Auth account and an 8–12 digit numeric PIN. The Owner email identifier is a server-only Render environment variable; the browser does not request an Owner email or send an Owner OTP/Magic Link.

## 3. Backend API contract

Base URL:

```text
https://shwqsljicmhflnvjqjah.supabase.co/functions/v1
```

Every Android Member request uses HTTPS and:

```http
Authorization: Bearer <Supabase access token>
Content-Type: application/json
apikey: <Supabase publishable/anon key>
```

| Function | Method | Request body | Purpose |
|---|---|---|---|
| `register-device` | POST | `device_label`, `app_version`, `android_version`, `android_api` | Create/refresh device registration; returns server-issued `device_id` |
| `get-my-repair-tools` | POST | `device_id` | Fetch the currently published and assigned catalogue for this device |
| `get-signed-repair-tool` | POST | `tool_id`, `device_id` | Fetch the current immutable signed recipe after fresh authorization |
| `submit-repair-run` | POST | `run_id`, `device_id`, `tool_id`, `tool_version`, timestamps, result, step results, app version | Submit an idempotent execution report |

The full field-level request/response contract is maintained in the Admin repository at `docs/ANDROID_API_CONTRACT.md`. That contract is authoritative; do not copy older GET endpoints or mock response shapes into new Android code.

## 4. Recipe signature and validation

- Algorithm: RSA-SHA256 / RSASSA-PKCS1-v1_5.
- Key size: RSA-3072.
- Encoding: sign and verify the exact UTF-8 bytes of the canonical JSON string.
- Android verifies the detached signature before parsing JSON.
- Android then validates schema version, tool/version identity, compatibility, step count, parameters, action IDs, risk level and user-confirmation requirements.
- Only native Kotlin implementations in `ActionRegistry.kt` can execute. Unknown actions are rejected.
- The private key stays in Supabase Edge Function Secrets. Android contains only the matching public key.

The Recipe v1 schema and permitted action parameters are shared by the Admin Edge Function schema and Android `RecipeContractVerifier.kt`.

## 5. Existing native action catalogue

The supported action IDs currently include:

`CHECK_INTERNET`, `DNS_LOOKUP`, `HTTPS_PROBE`, `CHECK_APP_INSTALLED`, `CHECK_STORAGE`, `OPEN_APP`, `OPEN_APP_DETAILS`, `OPEN_ANDROID_SETTINGS`, `SHOW_GUIDE`, `ASK_USER_CONFIRMATION`, `REQUEST_OWN_APP_PERMISSION`, `VERIFY_INTERNET`, and `VERIFY_DNS`.

A new website recipe can use any combination of already-compiled actions without rebuilding the APK. A new native capability or Action ID must be implemented and tested in the Android app, added to the Admin schema/builder, then released in a new APK.

## 6. Live synchronization and offline recovery

- While the app is foregrounded, it joins a private per-member/per-device Supabase Realtime channel. Messages carry only opaque event IDs; Android refetches the authorized catalogue through Edge Functions.
- The background periodic WorkManager catalogue refresh is configured for 15 minutes, using update policy so upgraded installations adopt the current interval. Android may defer work because of power, network and system scheduling; this is best-effort, not an exact 15-minute SLA.
- FCM background push is not yet configured. A closed/force-stopped app discovers new recipes on periodic refresh when scheduled or on its next launch.
- Repair reports are committed to a Room outbox before network upload. Each run has a stable UUID; WorkManager retries the same report ID with exponential backoff and the server rejects changed-content replays.
- Before every repair execution, Android fetches the latest signed release again. A cached catalogue entry alone is not authorization.

## 7. Build and test

From the Android repository root:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Build a release only with the protected production Android signing keystore:

```bash
./gradlew :app:assembleRelease
```

Do not confuse the Android APK release keystore with the separate RSA-3072 recipe-signing key. Preserve the same package ID and APK release certificate for in-place updates.
