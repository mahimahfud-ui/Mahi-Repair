# Mahi Repair — Personal Android Repair Toolkit
## Architecture, Backend API Contract & Setup Guide

**Mahi Repair** is a private Android repair toolkit designed exclusively for the Owner's family and trusted friends. It communicates with the Owner's **Mahi Repair Cloud** backend (Supabase) and executes published, digitally-signed repair recipes on non-rooted Android devices.

---

## 1. Core Architecture & Security Model

The application enforces strict defense-in-depth security principles:

1. **Zero Unrestricted Execution**:
   - The application **never** downloads or runs arbitrary shell scripts, bytecode, DEX, JAR, JavaScript, or dynamic libraries.
   - All repair capabilities are mapped to an **allowlisted registry of 13 native Kotlin actions**.
   - Any unknown action identifier causes immediate recipe rejection.

2. **RSA-SHA256 Digital Signature Verification**:
   - Every published recipe is signed by the Owner's private key (`SHA256withRSA`).
   - The Android client holds only the corresponding public verification key.
   - The client verifies the exact raw bytes of the recipe against the signature using Android Java Cryptography Architecture (JCA) APIs before parsing or executing any step.
   - Tampered payloads, mismatched signatures, or expired recipes are rejected and logged.

3. **Safe Android Troubleshooting**:
   - Zero root requirements.
   - No Accessibility Service abuse or Device Admin privileges.
   - Respects Android sandbox restrictions by guiding users to official system settings (e.g. `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `Settings.ACTION_WIFI_SETTINGS`).
   - Explicit user consent and guidance cards are displayed before sensitive operations.

4. **Multi-Tier Persistence & Offline Resilience**:
   - **DataStore**: Stores authenticated session tokens, email, role, and last sync timestamp.
   - **Room Database**: Caches published tool metadata and maintains an immutable local audit log of repair runs.
   - **WorkManager**: Background synchronization scheduled every 6 hours with `NetworkType.CONNECTED` battery constraints.

---

## 2. Supported Native Action Registry (13 Actions)

Every step in a repair recipe must specify one of the following 13 allowlisted actions:

| Action ID | Description | Parameters | Native Implementation |
| :--- | :--- | :--- | :--- |
| `CHECK_INTERNET` | Validates active network connectivity | None | Uses `ConnectivityManager` to inspect `NET_CAPABILITY_INTERNET` & `NET_CAPABILITY_VALIDATED`. |
| `DNS_LOOKUP` | Resolves target hostname | `hostname` (string) | Uses `InetAddress.getAllByName(hostname)`, measures resolution latency in milliseconds. |
| `HTTPS_PROBE` | Probes HTTP/HTTPS endpoint | `url` (string), `timeout_ms` (int) | Makes an HTTPS GET probe via `HttpURLConnection`, validates TLS handshake and HTTP 200/204 response. |
| `CHECK_APP_INSTALLED` | Verifies target app package status | `package_name` (string) | Queries `PackageManager.getPackageInfo()`, retrieves version name and enabled state. |
| `CHECK_STORAGE` | Inspects internal flash storage | `min_free_mb` (int) | Uses `StatFs(Environment.getDataDirectory().path)` to verify sufficient free space. |
| `OPEN_APP` | Launches target application | `package_name` (string) | Uses `PackageManager.getLaunchIntentForPackage()` with `FLAG_ACTIVITY_NEW_TASK`. |
| `OPEN_APP_DETAILS` | Opens App Info for Clear Cache | `package_name` (string), `instruction` (string) | Opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:<package_name>`. |
| `OPEN_ANDROID_SETTINGS` | Opens system settings screen | `setting_type` (WIFI, NETWORK, DATE, STORAGE) | Dispatches appropriate Android `Settings.ACTION_*` intent. |
| `SHOW_GUIDE` | Displays step-by-step guidance card | `guide_title`, `guide_description`, `guide_steps` | Suspends execution and displays an interactive modal card with numbered guidance steps. |
| `ASK_USER_CONFIRMATION` | Prompts user for explicit consent | `title`, `message`, `explanation` | Displays a confirmation dialog requiring user approval before continuing. |
| `REQUEST_OWN_APP_PERMISSION`| Verifies/requests app permission | `permission` (string) | Validates permissions via `ContextCompat.checkSelfPermission()`. |
| `VERIFY_INTERNET` | Post-repair internet verification | `host` (string) | Actively tests post-repair traffic to ensure problem resolution. |
| `VERIFY_DNS` | Post-repair DNS verification | `domain` (string) | Resolves domain post-repair to confirm resolution restored. |

---

## 3. Supabase Cloud Backend Integration

### 3.1 Environment Configuration (`.env` and `.env.example`)

Set your Supabase credentials in the AI Studio Secrets panel or in `.env`:

```properties
SUPABASE_URL="https://your-project-id.supabase.co"
SUPABASE_ANON_KEY="your-supabase-anon-key"
```

The app also provides an in-app **Connection Settings Dialog** (tap the gear icon in the top bar) to view connection status or update credentials dynamically on device.

### 3.2 Supabase Authentication (Email OTP)

The app integrates with Supabase Auth for invited family and friends:
1. User enters their invited email address.
2. The app issues `POST /auth/v1/otp` with body `{"email": "...", "create_user": false}`.
3. User enters the 6-digit OTP code received via email.
4. The app issues `POST /auth/v1/verify` with body `{"type": "email", "email": "...", "token": "..."}`.
5. The session token is saved securely in Jetpack DataStore.

### 3.3 Backend API Contract (Edge Functions)

Follow this contract in your separate Owner Admin Panel and Supabase Edge Functions:

#### 1. `get-my-repair-tools`
- **Method**: `GET`
- **Path**: `/functions/v1/get-my-repair-tools`
- **Headers**:
  ```http
  apikey: <SUPABASE_ANON_KEY>
  Authorization: Bearer <USER_ACCESS_TOKEN>
  ```
- **Response** (`200 OK`):
  ```json
  [
    {
      "id": "tool_youtube_repair",
      "name": "YouTube Quick Repair",
      "description": "Fixes video playback errors and clears corrupted cache.",
      "category": "Media & Streaming",
      "version": "1.2.0",
      "problem_description": "Video buffering, black screen on launch, or playback error 400.",
      "min_app_version": 1,
      "min_android_sdk": 26,
      "is_new": true,
      "enabled": true,
      "target_package": "com.google.android.youtube",
      "updated_at": 1730790000000
    }
  ]
  ```

#### 2. `get-signed-repair-tool`
- **Method**: `POST`
- **Path**: `/functions/v1/get-signed-repair-tool`
- **Headers**:
  ```http
  apikey: <SUPABASE_ANON_KEY>
  Authorization: Bearer <USER_ACCESS_TOKEN>
  Content-Type: application/json
  ```
- **Request Body**:
  ```json
  {
    "tool_id": "tool_youtube_repair"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "tool_id": "tool_youtube_repair",
    "version": "1.2.0",
    "signed_payload": "{\"recipe_id\":\"recipe_yt_01\",\"tool_id\":\"tool_youtube_repair\",\"version\":\"1.2.0\",\"required_android_sdk\":26,\"steps\":[...]}",
    "signature_base64": "MIIEvgIBADANBgkqhkiG9w0BAQEFAASC..."
  }
  ```

#### 3. `submit-repair-run`
- **Method**: `POST`
- **Path**: `/functions/v1/submit-repair-run`
- **Headers**:
  ```http
  apikey: <SUPABASE_ANON_KEY>
  Authorization: Bearer <USER_ACCESS_TOKEN>
  Content-Type: application/json
  ```
- **Request Body**:
  ```json
  {
    "run_id": "uuid-v4",
    "tool_id": "tool_youtube_repair",
    "tool_name": "YouTube Quick Repair",
    "tool_version": "1.2.0",
    "user_email": "friend@example.com",
    "device_model": "Google Pixel 8",
    "android_sdk": 34,
    "start_time": 1730790010000,
    "end_time": 1730790015000,
    "result": "SUCCESS",
    "final_diagnosis": "All repair and diagnostic steps passed successfully.",
    "steps": [
      {
        "step_id": "step_yt_net",
        "action": "CHECK_INTERNET",
        "title": "Check Internet Connection",
        "status": "COMPLETED",
        "message": "Internet connection active and validated (Wi-Fi).",
        "duration_ms": 120
      }
    ]
  }
  ```

---

## 4. Signing Recipes in the Admin Panel (RSA-SHA256)

When the Owner creates a recipe in the Admin Panel, sign the raw JSON string before publishing:

```python
# Python snippet for Admin Panel recipe signing:
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
import base64

def sign_recipe(recipe_json_str: str, private_key_pem: bytes) -> str:
    private_key = serialization.load_pem_private_key(private_key_pem, password=None)
    signature = private_key.sign(
        recipe_json_str.encode('utf-8'),
        padding.PKCS1v15(),
        hashes.SHA256()
    )
    return base64.b64encode(signature).decode('utf-8')
```

---

## 5. Building and Testing the Application

### 5.1 Run Unit and Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### 5.2 Build Debug APK
```bash
gradle :app:assembleDebug
```
The APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 5.3 Offline and Demo Mode
If the Supabase backend is pending deployment, users can tap **"Launch Verified Offline Toolkit"** on the sign-in screen to instantly test and run pre-signed verified recipes (YouTube Quick Repair, DNS Repair, Facebook Troubleshooter, Wi-Fi Troubleshooter, Play Store Repair).
