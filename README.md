# Mahi Repair — Personal Android Repair Toolkit (Web & React)

A comprehensive React port of the Mahi Repair ecosystem. Mahi Repair is a declarative, verifiable device diagnostic and repair system that runs immutable, signed repair recipes signed by the Owner's RSA-2048 key.

## Architecture

- **Client App (Android Device Toolkit View)**:
  - Allowlisted native repair action engine (`CHECK_INTERNET`, `DNS_LOOKUP`, `HTTPS_PROBE`, `CHECK_APP_INSTALLED`, `CHECK_STORAGE`, `OPEN_APP`, `OPEN_APP_DETAILS`, `OPEN_ANDROID_SETTINGS`, `SHOW_GUIDE`, `ASK_USER_CONFIRMATION`, `REQUEST_OWN_APP_PERMISSION`, `VERIFY_INTERNET`, `VERIFY_DNS`).
  - Cryptographic RSA-SHA256 signature verification via Web Crypto API (`RSASSA-PKCS1-v1_5` with `SHA-256`).
  - Bundled verified tools: YouTube Quick Repair, Facebook Troubleshooter, DNS Repair & Diagnostics, Wi-Fi Troubleshooter, Play Store Repair.
  - Interactive Step-by-step guidance and user confirmation modals.
  - Real-time diagnostic verdicts, failure detection, and execution audit history.
  - Backend connection configuration (Supabase URL, Anon Key, Public Key PEM).
  - Invited email OTP authentication and offline emergency mode.

- **Owner Cloud Console & Recipe Studio**:
  - Live overview metrics: total executions, success rate, active members, enrolled devices.
  - Tools library: create, edit, duplicate, disable, and publish recipes.
  - Script Studio & Recipe Composer: live JSON editor with syntax error checks, schema validation, and RSA signing.
  - Test Lab: pre-release dry-run simulator with virtual Android devices (Pixel 9 Pro, Galaxy S24, Pixel Tablet), real-time progress steps, and debug logs.
  - Members & Devices access control: invite family/team members, revoke compromised devices.

## Running Locally

```bash
npm install
npm run dev
```

Build for production:
```bash
npm run build
```
