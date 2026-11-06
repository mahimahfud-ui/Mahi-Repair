# Mahi Repair — Android CI & Test Verification Report

**Date**: November 6, 2026  
**Environment**: Linux x86_64, OpenJDK 21, Android SDK 36.1, Gradle 9.3.1  
**Test Suite Target**: `:app:testDebugUnitTest`  
**Overall CI Status**: **PASSED (30 / 30 Tests)**  

---

## 1. Test Execution Breakdown

All test suites were executed against the debug variant using local JVM and Robolectric 4.14.1 with Android 14 (API 34) instrumented shadows.

| Test Class | Tests Run | Passed | Failed | Errors | Execution Time |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.example.AppDatabaseReliabilityTest` | 3 | 3 | 0 | 0 | 0.386s |
| `com.example.ExampleRobolectricTest` | 1 | 1 | 0 | 0 | 0.081s |
| `com.example.ExampleUnitTest` | 1 | 1 | 0 | 0 | 0.001s |
| `com.example.PermissionExecutionFlowTest` | 5 | 5 | 0 | 0 | 9.637s |
| `com.example.RecipeEngineTest` | 10 | 10 | 0 | 0 | 4.669s |
| `com.example.RuntimePermissionContractTest` | 4 | 4 | 0 | 0 | 0.267s |
| `com.example.SubmitRunRequestContractTest` | 1 | 1 | 0 | 0 | 0.082s |
| `com.example.sync.SupabaseRealtimeClientTest` | 5 | 5 | 0 | 0 | 0.372s |
| **TOTAL** | **30** | **30** | **0** | **0** | **15.495s** |

---

## 2. Test Suite Details

### 1. `AppDatabaseReliabilityTest` (3 tests)
- `atomicReplaceOverwritesPreviousCatalogWithoutLeavingPartialRows`: Verifies that catalogue replacement in Room is strictly transactional. If any error occurs or new tools are saved, old entries are not left in an inconsistent or orphaned state.
- `outboxReportStoresExactPayloadForReliableRetry`: Verifies that `RepairRunReport` and its serialized JSON are persisted in the Room outbox, retaining the exact client `runId` across device restarts.
- `successfulAckDeletesReportFromOutbox`: Verifies that outbox items are pruned only when the server returns a confirmed acceptance of the exact matching `runId`.

### 2. `PermissionExecutionFlowTest` (5 tests)
- `permissionStepCompletesOnlyAfterAndroidReportsGranted`: Confirms that `REQUEST_OWN_APP_PERMISSION` does not transition to `COMPLETED` until the Android runtime permission callback returns `GRANTED`.
- `deniedPermissionFailsTheRequiredStepAndNeverReportsCompletion`: Confirms that user denial marks the step `FAILED` and halts recipe execution.
- `cancelledPermissionRequestCancelsTheRepair`: Confirms that dismissing the permission prompt transitions the recipe run to `CANCELLED`.
- `permanentlyDeniedPermissionIsNotTreatedAsGranted`: Confirms that `PERMANENTLY_DENIED` fails cleanly and directs the user to app settings.
- `repeatedResultCallbacksCannotTurnAnEarlierDenialIntoApproval`: Confirms that subsequent callbacks cannot mutate an already recorded denial.
- *Fix Applied*: Updated test harness keypair generator from 2048-bit to 3072-bit to comply with `RsaSignatureVerifier`'s strict RSA-3072 cryptographic policy.

### 3. `RecipeEngineTest` (10 tests)
- `validSignedRecipeExecutesAllowlistedActions`: Verifies that a validly signed recipe with allowlisted actions (`CHECK_INTERNET`, `DNS_LOOKUP`, etc.) executes successfully.
- `tamperedSignedPayloadIsRejected`: Verifies that any byte alteration in `signed_payload` invalidates signature verification.
- `unknownActionInRecipeIsRejected`: Verifies that unknown or arbitrary action strings fail validation before execution.
- `unsupportedPermissionRequestIsRejected`: Verifies that permissions not present in `RuntimePermissionContract` are rejected.
- `missingConfirmationOnHighRiskStepIsRejected`: Verifies that high-risk actions without explicit `requiresConfirmation` flag fail schema validation.
- `conditionalStepsHonorPreviousStepOutcome`: Verifies conditional evaluation (`condition.previousStep` and `condition.equals`).
- `signatureAlgorithmMismatchIsRejected`: Verifies rejection of any algorithm other than `RSASSA-PKCS1-v1_5` with `SHA-256`.
- `emptyStepsArrayIsRejected`: Verifies that empty recipe steps are rejected.
- `nonCanonicalJsonIsRejectedByDetachedSignature`: Confirms that detached signatures verify against exact UTF-8 bytes.
- `executionReportPreservesRunId`: Confirms that `RepairRunReport.runId` is an immutable UUID.

### 4. `RuntimePermissionContractTest` (4 tests)
- `allowlistContainsOnlyAuthorizedPermissions`: Confirms that only expected diagnostic permissions (e.g., `CAMERA`, `POST_NOTIFICATIONS`) are allowlisted.
- `dangerousSystemPermissionsAreExcluded`: Confirms that system permissions like `WRITE_SECURE_SETTINGS` or `PACKAGE_USAGE_STATS` are rejected.
- `permissionMappingPreservesManifestNames`: Confirms that internal aliases map to valid `android.Manifest.permission` constants.
- `nullOrEmptyPermissionIsRejected`: Confirms boundary validation.

### 5. `SubmitRunRequestContractTest` (1 test)
- `submitRunRequestJsonSerializesRunIdAndAppVersion`: Verifies that Moshi serializes `run_id`, `device_id`, `tool_id`, `tool_version`, `started_at`, `completed_at`, `result`, `step_results`, `error_code`, and `app_version` matching the Supabase Edge Function schema.

### 6. `SupabaseRealtimeClientTest` (5 tests)
- `connectSubscribesToExpectedDeviceTopic`: Verifies subscription to `mahi-repair:member:{userId}:device:{deviceId}`.
- `catalogChangedEventInvokesRefreshCallback`: Verifies that `catalog_changed` triggers a local catalogue sync.
- `memberAccessChangedEventInvokesAuthCheck`: Verifies that `member_access_changed` re-authenticates the member.
- `deviceRevokedEventTriggersLocalRevocation`: Verifies that `device_revoked` clears active device state and locks execution.
- `reconnectResubscribesWithoutDuplicatingListeners`: Verifies idempotency across network reconnects.

---

## 3. Lint & Static Analysis Audit

- **ProGuard / R8 Rules**: Preserved in `proguard-rules.pro` keeping Moshi adapters, Room entity models, and WebCrypto models.
- **Network Security Config**: Strictly enforces HTTPS and disables cleartext (`android:networkSecurityConfig="@xml/network_security_config"`).
- **Backup & Extraction Rules**: Explicitly excludes Keystore credentials, SharedPreferences tokens, and Room database from Cloud / ADB backups (`@xml/backup_rules`, `@xml/data_extraction_rules`).
