# Mahi Repair Cloud — Android Backend Integration Guide

This guide details how the Android application connects to the secure Google Cloud & Firebase staging backend.

---

## 1. Cloud Infrastructure & Configuration

| Parameter | Value |
|---|---|
| **Staging API Base URL** | `https://ais-dev-kahuk3nnzze6ngscnugunt-253592682542.asia-southeast1.run.app/api/v1` |
| **Local Dev Base URL** | `http://10.0.2.2:3000/api/v1` (Android Emulator) or `http://localhost:3000/api/v1` |
| **Firebase Project ID** | `manifest-hawk-76tvw` |
| **Firestore Database ID** | `ai-studio-mahirepair-a294799a-c294-4076-998a-6efc3ff9c0b7` |
| **Auth Domain** | `manifest-hawk-76tvw.firebaseapp.com` |
| **Signature Algorithm** | `RSASSA-PKCS1-v1_5` with `SHA-256` (RSA-3072) |

---

## 2. Recipe Verification Public Key (RSA-3072)

Embed this public key in the Android APK to verify all tool recipes before parsing or executing:

### Base64 DER Format (for `X509EncodedKeySpec`):
```text
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoO8m4oNIqIb0kGdLI6fWZKwS4Ffe0bfIS3XX2Z0lQV+Df+hOCvDIC/ADqiz2px9lVkiOKTZGSLCSJNOzFXXiqhG6s8vhp18BxbSIimTW7JY/JL6+Y/4leAQTuZOkj7EN9DHtwtUP6UusWUyDR4iB8/SRpxeK0VM1BMFvqXZlR+AhN5IALS1Gwz+UbpEZkPcGG/uiesAMuL3/jkRay+GD+ynCHzX6XS3CYJmH3GebGWDhZJlm8TOwUipCKm/fu1a1cKzrYu9pR7luBU1dOhQMnitaclEeovDKrXwyC3idwCrO5ovslKp2BwY9AxRDzv7qz1oc64EKNZnyis0HScvppGmaCf/NJJz//TpAkJy2MxwAc2n38jTyOtW1Om9UNNpjxjbddfXYwtLil78XGIZetDz+KzRaa9CdNKhdPAmwLhKf2KSNfOPCUp4dM/mAjEtK51I/puisNrGE6jYiyzOMHPR8jFDLm21N2/rbYMdyo1ptNEs2OBwLx4UefPaE4QHrAgMBAAE=
```

### Full X.509 PEM:
```text
-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoO8m4oNIqIb0kGdLI6fW
ZKwS4Ffe0bfIS3XX2Z0lQV+Df+hOCvDIC/ADqiz2px9lVkiOKTZGSLCSJNOzFXXi
qhG6s8vhp18BxbSIimTW7JY/JL6+Y/4leAQTuZOkj7EN9DHtwtUP6UusWUyDR4iB
8/SRpxeK0VM1BMFvqXZlR+AhN5IALS1Gwz+UbpEZkPcGG/uiesAMuL3/jkRay+GD
+ynCHzX6XS3CYJmH3GebGWDhZJlm8TOwUipCKm/fu1a1cKzrYu9pR7luBU1dOhQM
nitaclEeovDKrXwyC3idwCrO5ovslKp2BwY9AxRDzv7qz1oc64EKNZnyis0HScvp
pGmaCf/NJJz//TpAkJy2MxwAc2n38jTyOtW1Om9UNNpjxjbddfXYwtLil78XGIZe
tDz+KzRaa9CdNKhdPAmwLhKf2KSNfOPCUp4dM/mAjEtK51I/puisNrGE6jYiyzOM
HPR8jFDLm21N2/rbYMdyo1ptNEs2OBwLx4UefPaE4QHrAgMBAAE=
-----END PUBLIC KEY-----
```

---

## 3. Core Android Network & Execution Flows

### Flow A: Device Registration (App Startup)
1. On initial launch, generate device hardware profile and call:
   ```http
   POST /api/v1/devices/register
   Authorization: Bearer <Firebase_ID_Token>
   Content-Type: application/json

   {
     "device_label": "Pixel 8 Pro",
     "app_version": "1.0.0",
     "android_version": "15",
     "android_api": 35
   }
   ```
2. The server responds with `status: "registered"` and a server-issued `device_id`.
3. Store `device_id` securely in Android EncryptedSharedPreferences (excluded from auto-backup).
4. On subsequent app launches, include `device_id` in the body to record last-seen timestamps and verify that the device is not revoked.

### Flow B: Syncing Authorized Repair Tools
1. Fetch current signed tools authorized for the authenticated member:
   ```http
   POST /api/v1/recipes/my-tools
   Authorization: Bearer <Firebase_ID_Token>
   Content-Type: application/json

   {
     "device_id": "<saved_device_id>"
   }
   ```
2. For each tool returned, verify the detached `signature` over the exact UTF-8 bytes of `signed_payload` using `RecipeSignatureVerifier.kt`.
3. Cache verified recipes locally in Room database for offline emergency capability.

### Flow C: Verifying and Executing a Recipe
```kotlin
// Android Kotlin Signature Verification
val isVerified = RecipeSignatureVerifier.verifyAndParse(
    signedPayload = tool.signedPayload,
    signatureBase64 = tool.signature,
    publicKeyDerBase64 = RECIPE_SIGNING_PUBLIC_KEY_DER
)
// Only after verification passes, execute using RecipeExecutionEngine
```

### Flow D: Reporting Repair Telemetry
Upon recipe completion or user cancellation, submit telemetry:
```http
POST /api/v1/repair-runs
Authorization: Bearer <Firebase_ID_Token>
Content-Type: application/json

{
  "device_id": "<saved_device_id>",
  "tool_id": "tool_dns_repair",
  "tool_version": 1,
  "started_at": "2026-11-07T10:20:00.000Z",
  "completed_at": "2026-11-07T10:20:25.000Z",
  "result": "SUCCESS",
  "step_results": [
    { "step_id": "s1", "action_name": "CHECK_INTERNET", "status": "COMPLETED", "duration_ms": 120 },
    { "step_id": "s2", "action_name": "DNS_LOOKUP", "status": "COMPLETED", "duration_ms": 95 }
  ],
  "app_version": "1.0.0"
}
```

---

## 4. Admin Panel Unlock PIN API Contract

When porting administrative controls into the Android APK:
1. Require an authenticated account with `role == 'owner'`.
2. Prompt for the unlock PIN (`6993` default).
3. Call server-side verification:
   ```http
   POST /api/v1/admin/pin/verify
   Authorization: Bearer <Owner_Firebase_ID_Token>
   Content-Type: application/json

   {
     "pin": "6993"
   }
   ```
4. If verified, use the returned `adminSessionToken` to access administrative mutations.
5. The PIN is verified on the server using PBKDF2 with SHA-512 (100,000 iterations), constant-time comparison, and strict 5-attempt rate limiting.
