# Build and verification status

Last checked: 2026-11-06. This file records observed results, not a release certification.

## Repository and CI

| Area | Verified result | Evidence / limitation |
|---|---|---|
| Admin Panel dependency install and audit | PASS | `npm ci`; `npm audit --audit-level=moderate` reports 0 vulnerabilities on the verified build. |
| Admin Panel scaffold/security checks | PASS | GitHub Admin Panel CI. |
| Admin Panel TypeScript / ESLint / Vitest / Next.js production build | PASS | [Admin Panel CI run 42104142183](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42104142183), commit `3ddc9c5ef24702077e85c7e7a69e7b46a4729f1d`. |
| Supabase Edge Function inventory, Deno type checks and canonical contract tests | PASS | [Supabase CI run 42104142220](https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42104142220), commit `3ddc9c5ef24702077e85c7e7a69e7b46a4729f1d`. |
| Android unit tests and debug APK assembly | PARTIAL | Passed in an earlier Android CI run; the Compose emulator stage did not complete in that run. Current-head Android CI is being rerun. |
| Android Compose UI tests and source/APK secret scans | NOT YET VERIFIED | Awaiting completion of the current Android CI run. Do not treat skipped/cancelled steps as passes. |
| Physical Android device acceptance | NOT RUN | Requires a configured signing public key, real owner/member accounts and a physical device. |
| Render Admin Panel preview | LIVE | https://mahi-repair-admin-preview.onrender.com — owner login page loads; unauthenticated console routes require login. Current Node runtime is pinned to 22.23.4. |

## Live Supabase checkpoint

- Project `shwqsljicmhflnvjqjah` is ACTIVE_HEALTHY.
- The original three migrations are applied: `202611050001_initial_schema`, `20261105180000_harden_rls_grants_and_indexes`, and `20261105183000_deny_browser_rate_limit_access`.
- All 15 currently deployed Edge Functions are ACTIVE with JWT verification enabled.
- Security Advisor returned no lints.
- The PR's `20261106120000_private_realtime_catalog_broadcast.sql` is **not yet applied** to production.
- PR-branch Edge Function changes are **not yet deployed** to production.

## Required before production

1. Configure the matching RSA-3072 key pair: private PKCS#8 key directly in Supabase Edge Function Secrets; matching X.509 public key in Android build configuration. The checked-in public key is a placeholder.
2. Configure the protected GitHub `supabase-production` environment with `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, required reviewer protection and `ADMIN_ORIGIN` variable.
3. Configure Supabase Auth Site URL and the exact Render `/auth/callback` redirect allow list.
4. Verify the owner Auth account and manually bootstrap only that account to owner; no owner profile/data was present at the last database inspection.
5. Run the protected production workflow from `main` with the explicit `DEPLOY` confirmation after reviewing its migration dry-run.
6. Test member invitation, device registration, publication, assignment, signed delivery, run reporting, suspension and revocation end to end.
7. Complete Android emulator UI tests, source/APK secret scans, physical-device testing, key rotation/backup, monitoring, backup/restore rehearsal and privacy/retention review.

A passing Admin Panel build or Deno type check does not mean the production Supabase backend, owner account, signing key or Android application is fully operational.
