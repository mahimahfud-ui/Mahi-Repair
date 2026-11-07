# Mahi Repair — Personal Android Repair Toolkit
## Phase 2: Complete Supabase Backend Integration & Security Guide

**Mahi Repair** is a private Android repair toolkit designed exclusively for the Owner's family and trusted friends. It connects to the **Mahi-Repair Cloud** (Supabase backend) to synchronize published, digitally signed repair recipes and execute native diagnostics on Android devices.

---

## 1. Project Configuration & Identifiers

| Parameter | Configuration |
| :--- | :--- |
| **Product** | Mahi Repair |
| **Platform** | Native Android (Kotlin + Jetpack Compose + Material 3) |
| **Supabase Project Name** | `Mahi-Repair` |
| **Supabase Project ID** | `shwqsljicmhflnvjqjah` |
| **Supabase Project URL** | `https://shwqsljicmhflnvjqjah.supabase.co` |
| **Supabase Cloud Region** | `ap-south-1` |
| **Application ID** | `com.aistudio.mahirepair.qrzntk` |
| **Minimum Android SDK** | API 26 (Android 8.0 Oreo) |
| **Target Android SDK** | API 36 |

---

## 2. Complete Project Audit Findings

### 2.1 Implemented Components & Alignment
1. **Zero Unrestricted Code Execution**:
   - The application strictly enforces an allowlisted registry of **13 native Kotlin actions** in `ActionRegistry.kt`.
   - The backend schema validator in `supabase/functions/_shared/validator.ts` mirrors this allowlist 1:1.
   - Any unknown action, shell command, bytecode/DEX, or executable script is rejected immediately before parsing.
2. **Cryptographic Parity (RSA-SHA256)**:
   - The Owner generates an RSA-2048 keypair.
   - The private key resides **strictly server-side** as an Edge Function secret (`MAHI_OWNER_PRIVATE_KEY_PEM`).
   - The corresponding public key is embedded in `RsaSignatureVerifier.kt`.
   - Android JCA (`java.security.Signature.getInstance("SHA256withRSA")`) and Web Crypto API (`RSASSA-PKCS1-v1_5` with `SHA-256`) sign and verify the exact UTF-8 bytes of the canonical payload.
3. **Empty Catalog & Production Truth**:
   - Sample tools are **never silently substituted** when the server returns an empty catalog.
   - When 0 tools are published, Android reflects an empty database and displays the clean empty state.
   - Sample recipes are isolated strictly to local offline development/demo mode (`onContinueOffline`).
4. **Server-Side Authorization & Profile Validation**:
   - Client-submitted roles are ignored.
   - On every login and Edge Function request, the user's record in `profiles` is checked.
   - If `account_status != 'active'` (i.e. `suspended` or `revoked`), tool access and recipe execution are blocked immediately.
5. **No Leaked Credentials**:
   - The Android client only uses `SUPABASE_ANON_KEY` and the user's short-lived JWT.
   - No `service_role` key or private key is packaged into the APK.

---

## 3. Database Schema & Migration

The complete production migration is located at:
`supabase/migrations/20261105000000_mahi_repair_schema.sql`

### 3.1 Tables Overview
- **`profiles`**: Stores member records, roles (`owner`, `family_member`, `friend`), and statuses (`active`, `suspended`, `revoked`).
- **`devices`**: Tracks enrolled Android devices, manufacturer, model, SDK level, and sync timestamps.
- **`repair_tools`**: Catalog of repair tools, categories, audience rules (`all_active`, `assigned_only`, `owner_only`), and current versions.
- **`repair_tool_versions`**: **Immutable** version records storing the exact `signed_payload`, detached `signature`, and algorithm (`RSA-SHA256`).
- **`repair_tool_access`**: Per-user tool assignments when a tool is configured for `assigned_only`.
- **`repair_runs`**: Audit logs of all diagnostic executions with overall result (`SUCCESS`, `PARTIAL`, `FAILED`, `CANCELLED`).
- **`repair_run_steps`**: Granular execution timeline for each recipe step.
- **`admin_audit_logs`**: Immutable record of all administrative actions performed by the Owner.

### 3.2 Applying Migration
In your Supabase Dashboard -> **SQL Editor**, run the contents of `supabase/migrations/20261105000000_mahi_repair_schema.sql`.

---

## 4. Edge Functions Implementation (10 Endpoints)

Located in `supabase/functions/`:

### 4.1 User-Facing Functions
1. **`get-my-repair-tools`**:
   - **Method**: `GET`
   - **Path**: `/functions/v1/get-my-repair-tools`
   - **Auth**: Active member JWT
   - Returns only published tools that the user is authorized to access.
2. **`get-signed-repair-tool`**:
   - **Method**: `POST`
   - **Path**: `/functions/v1/get-signed-repair-tool`
   - **Body**: `{"tool_id": "uuid"}`
   - Returns the canonical `signed_payload`, `signature`, and `signature_algorithm`.
3. **`submit-repair-run`**:
   - **Method**: `POST`
   - **Path**: `/functions/v1/submit-repair-run`
   - **Body**: Execution report payload.
   - Binds `user_id` strictly to the authenticated caller's ID and inserts audit logs.

### 4.2 Owner-Facing Admin Functions (Owner Role Enforced)
4. **`admin-invite-member`**: Invites a family member or friend by email and initializes their profile.
5. **`admin-manage-member`**: Updates member role or sets account status (`active`, `suspended`, `revoked`).
6. **`admin-upsert-tool`**: Creates or edits tool definitions in draft/testing state.
7. **`admin-publish-tool`**: Validates recipe JSON against the 13 allowlisted actions, signs payload with the Owner's private key, writes an immutable version, and publishes tool.
8. **`admin-disable-tool`**: Immediately disables or archives a tool.
9. **`admin-assign-tool`**: Manages explicit member-to-tool assignments.
10. **`admin-get-analytics`**: Returns aggregated run summaries, success rates, failure diagnosis trends, and member counts.

---

## 5. RSA-SHA256 Key Pair Generation & Deployment

To establish the Owner's signing infrastructure:

### 5.1 Generate Keypair on Secure Machine
```bash
# 1. Generate 2048-bit RSA Private Key in PKCS#8 format
openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt > owner_private_key.pem

# 2. Extract Public Key in X.509 SPKI format
openssl rsa -in owner_private_key.pem -pubout > owner_public_key.pem
```

### 5.2 Configure Supabase Edge Function Secrets
Set the private key in your Supabase project secrets:
```bash
supabase secrets set MAHI_OWNER_PRIVATE_KEY_PEM="$(cat owner_private_key.pem)"
```

### 5.3 Configure Android Public Key
The Android application embeds the public key in `app/src/main/java/com/example/security/RsaSignatureVerifier.kt` (`DEFAULT_OWNER_PUBLIC_KEY`). Only recipes signed by the private key can be executed.

---

## 6. Supported 13 Native Actions Reference

| Action Identifier | Parameters | Behavior |
| :--- | :--- | :--- |
| `CHECK_INTERNET` | None | Checks active network and validated routes via `ConnectivityManager`. |
| `DNS_LOOKUP` | `hostname` (string) | Resolves host and measures resolution latency in milliseconds. |
| `HTTPS_PROBE` | `url` (string), `timeout_ms` (int) | Makes HTTPS GET probe and checks HTTP 200/204 response. |
| `CHECK_APP_INSTALLED` | `package_name` (string) | Checks application installation status via `PackageManager`. |
| `CHECK_STORAGE` | `min_free_mb` (int) | Validates internal storage space using `StatFs`. |
| `OPEN_APP` | `package_name` (string) | Safely launches installed application. |
| `OPEN_APP_DETAILS` | `package_name` (string), `instruction` (string) | Opens App Info for user-guided cache clearing. |
| `OPEN_ANDROID_SETTINGS` | `setting_type` (string: WIFI, NETWORK, DATE, STORAGE) | Navigates to native Android settings page. |
| `SHOW_GUIDE` | `guide_title`, `guide_description`, `guide_steps` | Suspends execution and displays an interactive guidance modal. |
| `ASK_USER_CONFIRMATION` | `title`, `message`, `explanation` | Asks user for explicit consent before proceeding. |
| `REQUEST_OWN_APP_PERMISSION` | `permission` (string) | Validates runtime app permissions. |
| `VERIFY_INTERNET` | `host` (string) | Post-repair connectivity verification. |
| `VERIFY_DNS` | `domain` (string) | Post-repair DNS resolution verification. |

---

## 7. Testing & Verification

### 7.1 Backend Unit & Security Tests
Run the Node.js test suite:
```bash
node --test supabase/tests/backend_test.mjs
```
**Results**:
- 7/7 tests passed:
  - Allowlisted action validator (all 13 actions accepted).
  - Shell command exploit rejection.
  - Bytecode/DEX loader exploit rejection.
  - Missing parameter rejection.
  - RSA-SHA256 signing and tampered payload rejection.
  - API response format alignment.
  - Empty catalog handling.

### 7.2 Android Unit & Robolectric Tests
Run the Android test suite:
```bash
gradle :app:testDebugUnitTest
```
**Results**:
- 11/11 tests passed:
  - `RecipeEngineTest`: all 13 action types, malicious action rejection, parameter checks, schema validation, RSA signature verification, tampered payload rejection, default Owner public key verification, bundled tools catalog.
  - `SupabaseDtoContractTest`: DTO JSON serialization/deserialization, `SignedRecipeResponse` with `signature` / `signature_base64`, `RemoteToolDto`, `RepairRunReportRequest`.
  - `ExampleRobolectricTest`: String resource context validation.

### 7.3 Android Build Verification
```bash
gradle :app:assembleDebug
```
Generates debug APK ready for device installation.
