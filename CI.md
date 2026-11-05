# Mahi Repair CI and release gates

Three GitHub Actions workflows validate the current PR branch:

- Android: `.github/workflows/android-ci.yml`
- Admin Panel: `.github/workflows/admin-panel-ci.yml`
- Supabase Edge Functions: `.github/workflows/supabase-functions-ci.yml`

## Android CI

- JDK 17 and Android SDK Platform 36.1 / Build Tools 36.0.0.
- Android unit/Robolectric tests and debug APK assembly.
- Compose UI tests on an Android emulator.
- Scan Android source for privileged secret markers.
- Inspect the generated APK for private signing keys and service-role credentials.
- Upload test reports and a debug APK artifact only when relevant checks succeed.

The artifact is a debug build, not a release-signed APK. The checked-in signing public key is a placeholder until the matching RSA-3072 public key is configured.

## Admin Panel CI

- Node.js 22.23.4, pinned consistently with Render.
- Deterministic `npm ci` from the committed package lock.
- Scaffold/security-integrity checks.
- `npm audit --audit-level=moderate` blocks moderate, high and critical advisories.
- TypeScript, ESLint, Vitest and Next.js production build.
- CI uses fake public Supabase values only; no live privileged credentials are required.

## Supabase Edge Function CI

Deno type-checks all 15 Edge Function entrypoints, shared modules and the canonical recipe/report contract tests. This workflow does not deploy to production.

## Protected production deployment

`.github/workflows/supabase-functions-deploy.yml` applies pending database migrations and deploys all 15 Edge Functions. It requires:

1. Dispatch from the `main` branch.
2. Input `confirm_production=DEPLOY`.
3. Approval by a required reviewer in the protected `supabase-production` GitHub environment.
4. GitHub environment secret `SUPABASE_ACCESS_TOKEN`.
5. GitHub environment secret `SUPABASE_DB_PASSWORD`.
6. GitHub environment variable `ADMIN_ORIGIN`.
7. The private signing key `RECIPE_SIGNING_PRIVATE_KEY_PEM` already present in Supabase Edge Function Secrets.

The production workflow verifies that the signing secret exists without reading its value, syncs `ADMIN_ORIGIN`, previews pending migrations, applies them, deploys all functions with JWT verification enabled and verifies migration history. It does not move the signing private key through GitHub.

Supabase injects `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` into the Edge Function runtime. These reserved values are not stored in Render or Android.

## Remaining acceptance gates

The PR Render preview is live, but production Realtime migration, Edge Function release, RSA private/public key provisioning, owner account bootstrap, owner/member/device end-to-end testing and physical-device acceptance remain necessary. A green CI build alone is not production readiness.
