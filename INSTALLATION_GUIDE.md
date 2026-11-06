# Mahi Repair — Android Installation & Operator Guide

This guide covers installing and operating the **Mahi Repair** native Android application on an Android device or emulator.

---

## 1. System Requirements

- **Android OS**: Android 8.0 Oreo (API Level 26) or higher.
- **Recommended**: Android 14+ (API Level 34+).
- **Network**: Internet connection for Supabase authentication, recipe synchronization, and report delivery.
- **Storage**: ~50 MB free internal storage.

---

## 2. Installing the APK

The installable debug APK is located at:
`Mahi-Repair-Installable.apk`

### Option A: Install via ADB (Android Debug Bridge)
Connect your device with USB Debugging enabled:
```bash
adb install -r Mahi-Repair-Installable.apk
```

If multiple devices/emulators are running:
```bash
adb -s <device-serial> install -r Mahi-Repair-Installable.apk
```

Launch the app:
```bash
adb shell am start -n com.aistudio.mahirepair.qrzntk/com.example.MainActivity
```

### Option B: Direct Device Sideloading
1. Transfer `Mahi-Repair-Installable.apk` to your phone via Google Drive, USB, or local file transfer.
2. Open the file on your device using the Files app.
3. If prompted, allow "Install unknown apps" for your file manager.
4. Tap **Install** and then **Open**.

---

## 3. First-Time Member Authentication

1. The Owner must first invite your email address through the Admin Console (`https://mahi-repair-admin.onrender.com`).
2. Open Mahi Repair on your phone.
3. Enter your invited email address on the sign-in screen.
4. Tap **Send Sign-in Code**.
5. Check your email inbox for the 6-digit OTP code sent by Supabase.
6. Enter the OTP code to complete authentication.
7. Upon successful login, the app will:
   - Register your Android device with the server.
   - Synchronize your authorized repair tool catalogue.
   - Connect to private Supabase Realtime for instant updates.

---

## 4. Running a Repair Tool

1. From the **Tools** tab, tap any published repair recipe (e.g. *YouTube Quick Repair*, *DNS Repair & Diagnostics*).
2. Review the tool summary, estimated runtime, and step actions.
3. Tap **Run Repair Tool**.
4. The execution engine performs the following:
   - Verifies the Owner's RSA-3072 cryptographic signature on the recipe payload.
   - Validates the step actions against the compiled native allowlist.
   - Prompts for confirmation on any high-risk diagnostic steps.
   - Executes diagnostics in sequence with real-time visual progress indicators.
5. Review the final diagnostic verdict and completion report.
6. The report is automatically saved to local history and queued in the durable outbox for server delivery to the Owner Admin Console.

---

## 5. Security Invariants

- **Cryptographic Guard**: Unsigned or tampered recipes cannot execute under any circumstance.
- **No Arbitrary Code**: Recipes are purely declarative JSON data mapped to compiled native Android SDK calls. No shell commands or dynamic scripts are executed.
- **Protected Secrets**: Authentication tokens are encrypted with hardware-backed Android Keystore keys. No passwords or tokens are stored in plain text.
