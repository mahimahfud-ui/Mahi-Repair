# Mahi Repair — execution checkpoint

Updated: 2026-11-07

## Repository and integration position

- Repository: https://github.com/mahimahfud-ui/Mahi-Repair
- Current integration branch: `integration/mahi-repair-release-candidate`
- Release candidate PR: https://github.com/mahimahfud-ui/Mahi-Repair/pull/8
- Previous candidate head inspected: `89553263c8a5f3f8c66dcde61cdaa54e44a8c792`
- Current core PR #1 head used as the integration parent: `fe3af5f3d178315ee4cf0807cf2dab008965bfce`
- PR #8 remains draft and unmerged. PR #1 remains open and unmerged.
- The integration commit `a521dc30147a53e65dff963e76e8aac5a29aeaf8` retains the release-candidate tree and resolves the two newer core commits as a second parent. PR #8 now targets the current core PR #1 head; GitHub reports the draft as mergeable, 138 commits ahead and zero behind. The release-candidate deployment/readiness documentation is retained as the current candidate-specific resolution; the Auth email contract test is unchanged between the two trees.
- Do not merge to `main`, deploy production, apply new production migrations, or make destructive production changes without explicit Owner approval.

## Verified GitHub and CI state

- Latest inspected candidate commit: `89553263c8a5f3f8c66dcde61cdaa54e44a8c792`.
- Admin Panel CI run #42273255288: PASSED, including dependency audit, scaffold/security checks, TypeScript, ESLint, Vitest and Next.js production build.
- Android CI run #42273255692: Android unit tests passed and `assembleDebug` passed. Compose emulator tests were still running at the last inspection; APK secret scan and artifact upload were pending that gate.
- The earlier Android emulator run #42264240707 failed because an assertion selected duplicate visible text for “Network Diagnostics”. The candidate test was revised to use stable semantics tags; the newer emulator run is the verification attempt.
- Supabase Edge Functions CI run #42273255674: PASSED on the aligned integration SHA. It type-checks the application Edge Functions and runs contract/migration integrity checks.
- Status labels apply only to the exact run and SHA above. A running/cancelled workflow is not a pass.

## Verified live Supabase state

- Project: `Mahi-Repair`, ref `shwqsljicmhflnvjqjah`, region `ap-south-1`, status `ACTIVE_HEALTHY`.
- Seven migration-history entries are present, including private Realtime, report idempotency, transactional admin audit, and atomic member catalogue/device registration.
- Nine public application tables were found; RLS is enabled on all nine.
- Fifteen application Edge Functions are active and all fifteen report `verify_jwt = true`.
- Deployed source matches the release-candidate source (after normalizing Supabase's packaged import paths) for: `submit-repair-run`, `register-device`, `get-my-repair-tools`, `get-signed-repair-tool`, `admin-upsert-tool`, `admin-publish-tool`, `admin-manage-member`, and `admin-manage-device`.
- Current database policies restrict profiles/devices to self or Owner reads, administrative data to Owner reads, tool data to Owners or assigned members, and private Realtime Broadcast topics to active Owners or the member's own non-revoked device.
- Public SECURITY DEFINER administrative/transactional RPCs are not executable by `anon` or `authenticated`; inspected grants permit `service_role`.
- The `supabase_realtime` publication is not configured to publish all tables.
- Live profile counts show one active Owner and one active Member. The other eight public application tables currently have zero rows: no registered devices, repair tools, signed versions, member assignments, device sync records, repair runs, audit rows or recipe-test history exist yet. The backend is connected, but there is no live repair catalogue for a Member to load until the Owner configures signing and publishes a real signed tool. No profile identifiers or email addresses are recorded here.
- The Send Email Auth Hook source/configuration exists in the candidate, but `send-auth-email` is not among the fifteen deployed functions and is not activated as a Supabase Auth Hook.

## Known blockers and unresolved verification

1. The root `.env.example` still contains `RECIPE_SIGNING_PUBLIC_KEY_PEM="REPLACE_WITH_MATCHING_PUBLIC_KEY_PEM"`. Android can compile with this placeholder but cannot verify production-signed recipes. Generate a matching RSA-3072 pair on a trusted machine; store only the PKCS#8 private key in Supabase Edge Function Secrets and configure only the matching X.509 public key in Android build configuration.
2. The matching `RECIPE_SIGNING_PRIVATE_KEY_PEM` and exact `ADMIN_ORIGIN` require secure project configuration. Do not paste secret values into chat or commit them.
3. The project security advisor reports leaked-password protection disabled. Enable it in Supabase Auth settings.
4. The performance advisor reports a missing covering index for `public.device_tool_syncs(tool_id, version)`. The other twenty unused-index notices are informational; do not remove indexes solely because the database has little workload.
5. A historical Auth log entry on 2026-11-06 recorded `429 over_email_send_rate_limit` for an email OTP request. The current candidate Owner login uses PIN/password sign-in; this historical OTP event does not prove that the current PIN flow fails.
6. The email hook still needs verified Resend/Brevo sender domains, provider secrets, a generated `SEND_EMAIL_HOOK_SECRET`, Auth Hook activation, and live delivery tests.
7. Real Owner login, Member OTP, device registration, private Realtime delivery/reconnect, repair execution, report upload/idempotency, and Admin visibility have not been verified end-to-end against a test account/device.
8. Physical Android-device testing, a release keystore, and a production-signed APK/AAB are not available as verified evidence.
9. The Admin hosting health endpoint could not be reached from this execution environment because DNS resolution failed. Hosting/runtime health and production URL remain unverified.

## Resumption sequence

1. Read this checkpoint and `docs/ACCEPTANCE_REPORT.md`.
2. Refresh PR #1, PR #8, the integration branch head, and workflow runs before editing.
3. Finish the current Android Compose emulator run. If it fails, read the complete failing test and fix the test or UI contract without weakening assertions.
4. Run/confirm all candidate CI checks on the latest integration SHA.
5. Validate migrations, RLS, idempotency concurrency and private Realtime on a disposable local/staging Supabase environment. Do not use production for destructive or synthetic-data tests.
6. Obtain the Owner-controlled signing key, Auth, provider, hosting and release-signing configuration through their secure dashboards.
7. Complete live staging Owner → Member/device → publish → signed catalogue → Android repair → report → Admin verification.
8. Update the acceptance report with exact run IDs, SHAs, URLs and observed outcomes. Production merge/deploy remains a separate Owner approval gate.


## Staging database constraint and fallback

- The approved Supabase development-branch request was rejected because the Primary organization is on the Free plan; Supabase database branching requires Pro or above. The quoted branch rate was $0.01344/hour. No branch was provisioned, so there is no active branch charge.
- A separate staging project was quoted at $0/month, but Supabase rejected creation because the organization has reached its two active Free-project limit. The two projects are `mahimahfud` and `Mahi-Repair`, both `ACTIVE_HEALTHY`.
- No existing project was paused or deleted and no subscription was upgraded. Production `Mahi-Repair` was not changed.
- Added `.github/workflows/supabase-database-ci.yml` and `admin-panel/supabase/tests/mahi_repair_database.test.sql`. GitHub Actions run #42277797821 PASSED: isolated Docker-backed Supabase stack started, all seven tracked migrations applied to a fresh local database, all 17 pgTAP checks passed, and the stack shut down cleanly. Synthetic Owner/Member/device/tool fixtures were rolled back.
- This GitHub-hosted local stack is an ephemeral CI test environment, not a persistent hosted staging project. Its CI result must be recorded separately from live-project acceptance.
- A real persistent hosted staging database still requires either a Pro upgrade for branching or a freed Free-project slot. Do not pause `mahimahfud` or upgrade the organization without explicit approval.
