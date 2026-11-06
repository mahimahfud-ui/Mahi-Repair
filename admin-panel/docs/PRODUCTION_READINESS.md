# Mahi Repair — production readiness

Use this checklist to distinguish implementation in the PR branch from work that must be completed against the live Supabase project and real Android devices.

## Implemented in the PR branch

- [x] Separate native Android client, private Next.js Admin Panel and Supabase backend.
- [x] Minimal Android home with one RUN REPAIR action and a published-tool picker after the user taps it.
- [x] Invite-only email OTP auth with account creation disabled.
- [x] Android Keystore-backed AES-GCM session storage and serialized refresh-token rotation.
- [x] Server-issued device identity and server-side member/device authorization.
- [x] Canonical Admin Panel recipe v1 schema, strict parameter validation and shared contract fixtures.
- [x] Canonical JSON serialization and RSA-SHA256 detached signing; Android verifies exact UTF-8 payload bytes before parsing.
- [x] Fresh signed recipe fetch and server reauthorization before execution.
- [x] Native action allowlist; no remote script, shell, DEX, JAR, APK or arbitrary code execution.
- [x] Private per-member/per-device Realtime Broadcast migration source and Android foreground WebSocket listener.
- [x] Opaque Realtime events only; Android refetches and verifies the authorized catalogue. Launch/foreground/reconnect and WorkManager sync provide recovery.
- [x] Admin Panel dependencies remediated to zero reported npm vulnerabilities; CI blocks moderate/high/critical advisories.
- [x] Node.js 22.23.4 pinned across Render, Admin Panel and Admin Panel CI.
- [x] Protected production workflow source for migration preview/apply and JWT-verified deployment of all 15 Edge Functions.
- [x] Render Free preview is live and owner-only routes were smoke-tested.

## Production configuration still required

- [ ] Generate a real RSA-3072 key pair. Add only the PKCS#8 private key directly to Supabase Edge Function Secrets as `RECIPE_SIGNING_PRIVATE_KEY_PEM`; configure the matching X.509 public PEM in Android build configuration.
- [ ] Configure the protected GitHub `supabase-production` environment with required reviewer, secrets `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`, and variable `ADMIN_ORIGIN`.
- [ ] Verify the private signing secret is present in Supabase before deploying.
- [ ] Configure Supabase Auth Site URL and the actual Admin Panel `/auth/callback` redirect allow list.
- [ ] Confirm the verified owner Auth account and manually set only that profile to owner; new profiles intentionally default to member.
- [ ] Apply the pending migration `20261106120000_private_realtime_catalog_broadcast.sql` using the protected production workflow after reviewing its dry-run.
- [ ] Disable Supabase Realtime **Allow public access** and verify private `realtime.messages` RLS against owner/member/device cases.
- [ ] Deploy the PR-branch Edge Function updates with JWT verification enabled through the protected workflow.

## Acceptance tests still required

- [ ] Owner login, member invitation, OTP login, session refresh and expired-session reauthentication.
- [ ] Device registration, duplicate registration handling, device revoke/restore and suspended-member denial.
- [ ] Publish a first real signed recipe; verify canonical bytes, RSA signature, version and key match on a physical device.
- [ ] Publish a new recipe using existing allowlisted native actions and confirm the already-installed app receives it without APK reinstall.
- [ ] Verify update, disable/archive, assignment removal, member suspension and device revocation remove access and prevent fresh execution.
- [ ] Verify private Realtime topic isolation, foreground reconnect, missed-event recovery, process restart and offline behavior.
- [ ] Complete Android emulator Compose UI tests, source/APK privileged-secret scans and upload a debug artifact.
- [ ] Test each native action's parameters, confirmation, permission handling and Android-version compatibility on physical devices.
- [ ] Establish signing-key rotation/revocation and secure backup procedure.
- [ ] Complete telemetry consent, privacy/retention/deletion policy, function monitoring, alerting and backup/restore rehearsal.

## Release constraints

- The Render Free preview is not the production Admin Panel service.
- A remote recipe can only use capabilities already compiled into the installed APK. Adding native Kotlin behavior or a new Android permission requires an app update.
- Realtime is foreground-only. When the process is stopped or the device is offline, updates are recovered on the next app launch/resume/reconnect/periodic sync; immediate background delivery is not guaranteed.
- The production workflow must remain restricted to `main`, explicit `confirm_production=DEPLOY` and the protected `supabase-production` environment.
- Do not merge the draft PR or claim production readiness until the required production configuration and acceptance tests have evidence.
