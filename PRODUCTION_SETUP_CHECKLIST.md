# Mahi Repair — production setup and acceptance checklist

**Production project:** `shwqsljicmhflnvjqjah`  
**Owner Admin:** `https://mahi-repair-admin.onrender.com`  
**Android app repository:** `mahimahfud-ui/Mahi-Repair`  
**Admin repository:** `mahimahfud-ui/Mahi-Repair-Admin`

This checklist records the current production architecture. Do not re-run already-applied migrations or redeploy active functions unless the source has actually changed.

## 1. Existing infrastructure — verified

- Supabase project is `ACTIVE_HEALTHY`, PostgreSQL 17.
- All seven repository migrations are in production migration history.
- All 15 Edge Functions are `ACTIVE` with `verify_jwt=true`.
- RLS is enabled on every public table and on `realtime.messages`.
- Render Owner Admin is connected to the Admin repository's `main` branch. Its latest Admin security-hardening commit has passed CI and its Render deployment is being rolled out.
- Render is configured with the production Supabase URL, the enabled public publishable key, and server-only `ADMIN_OWNER_EMAIL`.
- Existing active Owner is `mahinmhfd@gmail.com`; existing active Member is `mahimahfud@gmail.com`. Never promote, rename or modify the Member to make an Owner login work.

## 2. Required secret boundaries

- Supabase Edge Functions require `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `ADMIN_ORIGIN`, and the signing private key as appropriate.
- Keep service-role and RSA private-key material only in Supabase server-side secrets.
- Android `.env.example` contains only public Supabase client configuration and an RSA-3072 public key. It must never contain a service-role key, Owner PIN, password, SMTP credential or signing private key.
- `RECIPE_SIGNING_PRIVATE_KEY_PEM` must be a valid RSA-3072 PKCS#8 private key that cryptographically matches the Android `RECIPE_SIGNING_PUBLIC_KEY_PEM`. Its actual production value and match are not exposed by the connected tooling and remain unverified. Do not publish any tool until an operator verifies the pair securely.
- `ADMIN_ORIGIN` must be the canonical HTTPS origin `https://mahi-repair-admin.onrender.com`, without a path or trailing slash.

## 3. Owner authentication

1. Open `https://mahi-repair-admin.onrender.com/login`.
2. Use the existing Owner Auth account and the configured 8–12 digit PIN.
3. The server must authenticate the configured Owner identity, then verify `profiles.role='owner'` and `profiles.status='active'`.
4. Verify the Owner console opens and every protected route denies a Member session.
5. Do not use the Member email as an Owner fallback. Do not change either account's role or credentials during acceptance.

A successful Owner PIN login has not yet been recorded in production. The Owner must complete this normal, user-initiated check; no automated test sign-in is performed.

## 4. Member email and device enrollment

1. Verify the production email provider/SMTP sender configuration in Supabase Auth before inviting a real Member. Do not send test or unsolicited emails.
2. From the Owner console, invite a real Member.
3. The invited Member signs in to the Android app using the emailed OTP.
4. Android calls the `register-device` Edge Function with its authenticated Member JWT and device/app/Android metadata.
5. Confirm the device appears under the same Member in Admin.
6. Confirm that revoked or suspended accounts and revoked devices are rejected by subsequent requests.

Current production contains 0 registered devices. OTP delivery and a physical Android enrolment have not yet been verified. Supabase Security Advisor also warns that leaked-password protection is disabled; enable it in Supabase Auth security settings.

## 5. Tool publishing and signed recipe verification

1. Confirm the production signing private/public key pair matches before publishing.
2. Create a tool in Owner Admin and validate its JSON against the allowlisted recipe schema.
3. Run the Admin Test Lab/validation workflow.
4. Publish a version; confirm the server stores a canonical payload and RSA-SHA256 signature.
5. Assign the tool to the test Member or all active Members.
6. Android fetches the Member/device-authorized catalogue, verifies the signature and schema, and refuses invalid, revoked, incompatible or unassigned content.
7. Test disable, unpublish, version replacement and device revocation.

Current production contains 0 repair tools and 0 published versions. No production signing-key match or signed-tool round trip has been verified.

## 6. Repair execution, idempotency and Realtime

1. Run a safe read-only diagnostic on a real supported Android device.
2. Verify the native action result is displayed and recorded.
3. Submit a repair report; confirm the Admin panel sees the persisted record.
4. Replay the exact same report ID/content and confirm duplicate idempotent success.
5. Reuse the same report ID with changed content and confirm conflict rejection.
6. Verify private Realtime invalidation for the Owner console and the Member's own device only.
7. Revoke the device and confirm the next catalogue/report request is denied.

Current production contains 0 repair runs. Realtime RLS policies/triggers exist, but actual event delivery to a physical Android client has not been demonstrated.

## 7. Release build

- CI currently targets a debug APK for test installation.
- The production release build requires the correct existing Android upload keystore and `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` configuration. These values are not present in the repository and must not be fabricated.
- Do not replace the existing app signing identity; a new keystore can prevent updates over the installed app.
- After release signing is configured, produce and verify an APK/AAB, install it on a physical device and complete the end-to-end checks above.

## 8. Optional legacy Mahi API and remote support

`MAHI_API_BASE_URL` is not the Admin website. The standalone Express backend currently exports a file-backed `MemoryStagingDb` and seeds demo users/devices. It is not safe to deploy as production storage and does not have a deployed Render service. Keep device telemetry/remote-support API calls disabled until a new durable Supabase-backed implementation, authentication, CORS, rate limiting and session lifecycle have been designed and tested.

## 9. Observability and final release gate

- Add privacy-safe structured Render request logs for `/login`, `/auth/callback`, Owner authorization outcomes and API 4xx/5xx.
- Current connected Render log stream has no `request` log type; Auth/email-delivery logs were also unavailable in the latest Supabase log stream.
- Verify SMTP delivery, Supabase Auth security settings, private Realtime project-level setting, backup/restore, alerting and key rotation procedure.
- Record a test report with timestamps, app version, device/API level, test tool version and pass/fail for each acceptance check.

**Release decision: NOT FULLY PRODUCTION READY** until Owner login, Member OTP, device registration, signing-key match, one signed published tool, one persisted repair report, private Realtime, production release signing and physical-device acceptance are proven.
