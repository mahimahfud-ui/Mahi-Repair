# Mahi Repair — acceptance report

Updated: 2026-11-07  
Repository: https://github.com/mahimahfud-ui/Mahi-Repair  
Integration PR: https://github.com/mahimahfud-ui/Mahi-Repair/pull/8

This report distinguishes source implementation, CI evidence, live Supabase inspection and real end-to-end acceptance. “Implemented” is not equivalent to “Verified”.

## Summary

The live Supabase project is healthy, has the expected application schema and RLS, and is serving the report/device/catalogue/admin functions. Key deployed Edge Function sources match the integration candidate. The Admin Panel production build passes CI. Android unit tests and debug APK assembly pass on the latest inspected candidate run, but Compose emulator tests are still in progress. A complete Android → Supabase → Admin live workflow is not yet proven. The product is **not production ready**.

## Evidence matrix

| Area | Status | Evidence / limitation |
|---|---|---|
| GitHub repository access and branch/PR inspection | VERIFIED | Owner-level repository access; PRs #1–#8 inspected. PR #8 remains a draft; no PR merged. |
| Integration candidate history | VERIFIED | Candidate history was aligned with current core PR #1 head `fe3af5f3d178315ee4cf0807cf2dab008965bfce` as a merge parent. Candidate source tree retained. |
| Admin Panel | VERIFIED (CI) | Run #42271769418 passed dependency/security audit, TypeScript, ESLint, Vitest and Next.js production build. Live hosting and real Owner sign-in remain unverified. |
| Android unit tests | VERIFIED (CI) | Run #42271769257 reached Compose stage after unit tests passed. |
| Android debug APK assembly | VERIFIED (CI) | Run #42271769257 successfully assembled the debug APK. It is not a release-signed APK. |
| Android Compose UI tests | IN PROGRESS | Run #42271769257 was at emulator Compose tests at last inspection. Do not call this passed until GitHub reports success. |
| Android generated-APK secret scan / artifact upload | NOT TESTED | These steps were pending the Compose gate at last inspection. |
| Supabase project connectivity | VERIFIED | Project `shwqsljicmhflnvjqjah` reports `ACTIVE_HEALTHY`; PostgreSQL 17. |
| Supabase migrations | VERIFIED (history) | Seven migration-history entries found, including report idempotency and atomic catalogue/device changes. Fresh-database and upgrade tests on a disposable database remain unverified. |
| Public-table RLS | VERIFIED (configuration inspection) | All nine application tables report RLS enabled. Runtime cross-user denial tests remain unverified. |
| Supabase Edge Functions | VERIFIED (deployed inventory) | Fifteen application functions active; all fifteen require JWT verification. `send-auth-email` is not deployed/activated. |
| Android/backend report contract | IMPLEMENTED; runtime NOT TESTED | Client persists immutable outbox payloads and stable run UUIDs; server uses transactional idempotency. Live first-submit/duplicate/conflict/concurrency tests have not been executed. |
| Deployed Edge Function source parity | VERIFIED (source comparison) | Eight critical deployed functions match candidate source after normalizing packaged import paths. This does not replace request-level E2E tests. |
| Private Realtime | IMPLEMENTED; runtime NOT TESTED | Private active-owner and own-device member policies inspected; publication is not configured for all tables. Actual WebSocket delivery, revocation and reconnect tests remain outstanding. |
| Admin Owner PIN | IMPLEMENTED; live login NOT TESTED | Server action submits the PIN as the existing Owner Auth password and checks the active Owner profile. Owner PIN and hosting configuration must be set through secure administration. |
| Security advisor | BLOCKED / ACTION REQUIRED | Leaked-password protection is disabled in Supabase Auth. |
| Performance advisor | ACTION REQUIRED | Missing covering index for `device_tool_syncs(tool_id, version)`; unused-index notices are informational pending workload. |
| RSA recipe signing | BLOCKED | Android public key remains a placeholder; matching RSA-3072 private key must be configured only in Supabase Edge Function Secrets. |
| Auth email delivery | BLOCKED | Send Email Hook is not deployed/activated; verified provider senders, secrets and hook secret are still required. Historical OTP logs contain a 429 rate-limit response. |
| Staging hosting | NOT VERIFIED | Health URL could not be reached from this environment; runtime URL, HTTPS, callback origin and DB connectivity need a live smoke test. |
| Physical Android device | NOT TESTED | Emulator testing cannot prove physical-device acceptance. |
| Release APK/AAB | BLOCKED | No production upload keystore/signing evidence is available. |
| Full Android → Supabase → Admin workflow | NOT TESTED | Requires real Owner/Member accounts, signing configuration, a test device, Realtime and report-round-trip evidence. |
| Production merge/deployment | BLOCKED BY APPROVAL | No `main` merge, production deployment, production migration or production data modification was performed. |

## Live Supabase inspection details

- Project status: `ACTIVE_HEALTHY`; region `ap-south-1`; PostgreSQL 17.
- Public tables: `profiles`, `devices`, `repair_tools`, `repair_tool_versions`, `repair_tool_access`, `device_tool_syncs`, `repair_runs`, `admin_audit_logs`, `recipe_test_history`. All reported RLS enabled.
- Applied migration history: initial schema, RLS/grants/index hardening, rate-limit access control, private Realtime broadcast, report idempotency/atomic catalogue, transactional admin audit, and atomic member catalogue/device registration.
- Security-definer application RPCs inspected were executable by `service_role`, not `anon` or `authenticated`.
- Auth profile counts: one active Owner and one active Member.
- Current live application data counts: `profiles=2`; `devices=0`, `repair_tools=0`, `repair_tool_versions=0`, `repair_tool_access=0`, `device_tool_syncs=0`, `repair_runs=0`, `admin_audit_logs=0`, and `recipe_test_history=0`. The live backend is connected, but no device or repair catalogue content is populated; a Member will not have a tool list until an Owner configures RSA signing and publishes a real signed recipe.

## CI references

- Android current candidate run: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42273255692
- Admin Panel current candidate run: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42273255288
- Supabase Edge Functions CI on unchanged backend source: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42273255674
- Earlier Android Compose failure used to identify the duplicate-text assertion: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42264240707

## External gates

1. Generate the RSA-3072 signing key pair on a trusted machine. Put only the private key in Supabase Edge Function Secrets and only the matching public key in Android build configuration.
2. Enable Supabase Auth leaked-password protection and verify the configured token/password rate limits.
3. Verify Resend and Brevo sender domains, configure their secrets, deploy `send-auth-email`, generate `SEND_EMAIL_HOOK_SECRET`, enable the Auth Send Email Hook, and exercise Android OTP plus invitation delivery.
4. Configure the real Admin hosting origin, `ADMIN_OWNER_EMAIL`, callback URL and exact Edge Function `ADMIN_ORIGIN`. Verify the Owner PIN and a Member denial.
5. Run SQL/RLS/idempotency/concurrency and private Realtime tests on a disposable staging/local Supabase database, not against production.
6. Install the release-signed APK on a physical Android device and complete the full Member/device/repair/report/Admin journey.
7. Obtain explicit Owner authorization before any merge to `main`, production migration or production deployment.

## Release decision

**NOT PRODUCTION READY.** The codebase and live backend have substantial implemented functionality, but Compose CI, live staging acceptance, the RSA signing configuration, Auth email activation, hosting verification, release signing and physical-device tests are outstanding.


## Hosted staging provisioning result

The requested Supabase development branch could not be created: the Primary organization is Free-plan and Supabase requires Pro or above for database branching. The quoted branch cost was $0.01344/hour; no branch was provisioned and no recurring branch charge is active.

A separate project was quoted at $0/month, but project creation was rejected because the organization is at its two active Free-project limit (`mahimahfud` and `Mahi-Repair`, both active/healthy). No project was paused/deleted and no subscription was upgraded. The production Mahi-Repair project was not modified.

## Ephemeral database test path

Added:
- `.github/workflows/supabase-database-ci.yml` — starts a Docker-backed local Supabase stack in GitHub Actions, recreates its database from the tracked migration directory, runs pgTAP tests, and stops the stack.
- `admin-panel/supabase/tests/mahi_repair_database.test.sql` — synthetic Owner/Member RLS checks, direct-write denial, core-table RLS coverage, RPC privilege checks, and report first-submit / exact-retry / changed-fingerprint / single-row tests. Fixtures are rolled back in the test transaction.

This verifies the migration set against a fresh local Supabase database if the workflow passes; it is not proof of a persistent hosted staging environment or live production behavior. Database CI run #42277797821 PASSED on candidate `c9e71c0ebf3c5bace865f28c4bf79363a9519498`: the isolated local stack started, all seven tracked migrations applied on a fresh database, all 17 pgTAP RLS/idempotency checks passed, and the stack shut down cleanly.

## Persistent staging decision still required

To obtain a persistent hosted staging database, either upgrade the Primary Supabase organization to Pro (branching becomes available) or free a Free-project slot by pausing an existing project. No existing project will be paused/deleted and no subscription will be upgraded without explicit approval.


## Database CI result

**Result: PASSED.**

The first fresh-database run passed on integration commit `c9e71c0ebf3c5bace865f28c4bf79363a9519498`:

- Supabase local stack start: passed.
- Fresh local database reset and all seven tracked migrations: passed.
- pgTAP suite: all 17 planned checks passed.
- Local stack shutdown: passed.

The 17 checks cover:
1. Member reads only their own profile.
2. Member cannot read the Owner profile.
3. Member reads only their own device.
4. Member sees published tools, not draft tools.
5. Member sees only the accessible signed recipe version.
6. Member cannot read Owner audit rows.
7. Member cannot write repair reports directly.
8. Owner can read both profiles.
9. Owner can read published and draft tools.
10. RLS is enabled on all nine core application tables.
11–13. Report RPC execute grants are denied to anon/authenticated and granted to service_role.
14. First report submission is accepted as non-duplicate.
15. Exact retry returns the duplicate receipt.
16. Reuse of a run ID with a different fingerprint returns conflict.
17. Exactly one report row is stored after first submit plus retry.

All test data is synthetic and the SQL test ends with ROLLBACK. This result is from an ephemeral GitHub-hosted local Supabase stack, not from a persistent cloud staging project or production.


## Latest candidate verification — 2026-11-07

The status in earlier sections is superseded by this exact candidate result: `3207acb104a1ee390ba0a9fa621ae234d5bcfd01`. All four workflows passed: Android #42356155144 (including 6/6 Compose tests and APK source/generated-secret scans), Admin #42356155163, Edge Functions #42356155152, and Database #42356155031 (seven fresh migrations and 17 pgTAP checks).

The debug APK artifact is [#13567644352](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42356155144/artifacts/13567644352). Extracted APK SHA-256: `9e2a786b1683d6c4bfc6cb40adf85be9e4eb85586e8d3a31fa63dfe0f66e9aec`. It is debug-signed with an ephemeral key, not a production release.

Production acceptance is still outstanding: no live devices or published catalogue entries exist; the recipe-signing public key is a placeholder; the Auth email hook is not deployed/active; leaked-password protection is disabled; hosted staging is unavailable on the current Free-plan limits; Cloud Run and production release signing are not verified; and no physical-device Owner/member/report round-trip has been tested. The product remains NOT PRODUCTION READY.
