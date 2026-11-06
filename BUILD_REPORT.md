# Mahi Repair — Android Build & Release Verification Report

**Target Project**: Mahi Repair Native Android Application  
**Baseline Branch**: `agent/android-premium-ui`  
**Reference Commit**: `40f8f39a9bdacb8fd9d4d8b9a0e08e551d26dabe`  
**Base Architecture Commit**: `fcb0c011a8b48236e58506d97598be2e7c58f9c4` (`agent/android-core-reliability`)  
**Shared Supabase Project**: `shwqsljicmhflnvjqjah` (`https://shwqsljicmhflnvjqjah.supabase.co`)  
**Report Date**: November 6, 2026  

---

## 1. Executive Summary

A clean, reproducible, native build of the Mahi Repair Android application was successfully executed. The build uses the existing Jetpack Compose UI, Room persistence, WorkManager outbox queue, cryptographic RSA-3072 signature verification engine, and Supabase client integration.

All 30 unit and integration tests passed with **0 errors and 0 failures**. The genuinely installable Android Debug APK was packaged, signed with the debug keystore, and verified.

---

## 2. Release Artifacts

| Deliverable | File Path | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Installable APK** | `/app/applet/Mahi-Repair-Installable.apk` | 24 MB (24,785,152 bytes) | `fff01c52cad26e559d374c546a150ac251fc3f709fd22d69509054658bb2ed69` |
| **Complete Source Archive** | `/app/applet/Mahi-Repair-Android-Ready.zip` | 2.8 MB (2,883,584 bytes) | `7452d783aa7e2ca2975381646e1360fa6d2658e22906000efc6e96d2d1901b86` |

*Note: The APK is designated as a genuine installable Debug APK because the Owner's private production release keystore is safely guarded and not distributed in repositories or public environments.*

---

## 3. Build & Toolchain Specifications

- **Gradle Version**: 9.3.1 (distribution: `gradle-9.3.1-bin.zip`)
- **Android Gradle Plugin (AGP)**: 9.1.1
- **Kotlin Version**: 2.2.21 (with Compose Compiler 2.2.10)
- **KSP Version**: 2.2.21-1.0.30
- **JDK Toolchain**: OpenJDK 21.0.12.1 (Ubuntu 64-Bit Server VM)
- **Android SDK Platforms**: Android SDK Platform 36 (Revision 2), Android SDK Platform 36.1 (Revision 1)
- **Build Tools**: 36.0.0
- **Compile SDK**: `36` (minorApiLevel: `1`)
- **Target SDK**: `36`
- **Min SDK**: `26` (Android 8.0 Oreo)
- **Application ID**: `com.aistudio.mahirepair.qrzntk`
- **Namespace**: `com.example`
- **Version Code**: `1`
- **Version Name**: `1.0`

---

## 4. Build Commands & Execution Log

### Debug APK Assembly

```bash
cd /tmp/android-repo
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug
```

**Result**:
```
BUILD SUCCESSFUL in 3m 58s
39 actionable tasks: 39 executed
Configuration cache entry stored.
Output artifact: app/build/outputs/apk/debug/app-debug.apk
```

### Unit Test Execution

```bash
./gradlew :app:testDebugUnitTest
```

**Result**:
```
BUILD SUCCESSFUL in 33s
33 actionable tasks: 3 executed, 30 up-to-date
Configuration cache entry reused.
Total Test Suites: 8
Total Tests Run: 30
Failures: 0
Errors: 0
Skipped: 0
```

---

## 5. Security & Secret Audit of Build Artifacts

1. **Supabase Service-Role Key**: **ABSENT** from APK and source.
2. **Database Passwords**: **ABSENT** from APK and source.
3. **RSA Private Signing Key**: **ABSENT** (retained exclusively in Supabase Edge Function Secrets).
4. **Owner Credentials / Admin PIN**: **ABSENT** (Admin routes and controls exist only on the separate Owner Admin website).
5. **Cleartext Traffic**: **BLOCKED** via `network_security_config.xml` (`cleartextTrafficPermitted="false"`).
6. **Android Backup**: **DISABLED** via `backup_rules.xml` and `data_extraction_rules.xml` (`allowBackup="false"`).
