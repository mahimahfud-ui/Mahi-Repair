# Mahi Repair CI and release gates

Three GitHub Actions workflows validate the PR branch:

- Android: `.github/workflows/android-ci.yml`
- Admin Panel: `.github/workflows/admin-panel-ci.yml`
- Supabase Edge Functions: `.github/workflows/supabase-functions-ci.yml`

Push and pull-request events use the same branch-based concurrency group, so the same branch is not built twice for the two event types.

## Android CI

- JDK 17 and Android SDK Platform 36.1 / Build Tools 36.0.0.
- The repository includes the Gradle wrapper launcher and wrapper JAR; `gradle/wrapper/gradle-wrapper.properties` pins Gradle 9.3.1.
- Android unit/Robolectric tests and debug APK assembly.
- Compose UI tests on an Android emulator.
- Android source scan for privileged secret markers.
- Generated APK scan for private signing keys and service-role credentials.
- Test reports and a debug APK artifact upload when the respective checks succeed.

The APK artifact is a debug build, not a release-signed production artifact. The checked-in signing public key is a placeholder until the real matching RSA-3072 public key is configured.

## Admin Panel CI

- Node.js 22.23.4, pinned consistently with Render.
- Deterministic `npm ci` from the committed lockfile.
- Scaffold/security-integrity checks.
- `npm audit --audit-level=moderate` blocks moderate, high and critical advisories.
- TypeScript, ESLint, Vitest and Next.js production build.
- CI receives fake public Supabase values only; no live privileged credential is required.

## Supabase Edge Function CI

Deno type-checks all 15 Edge Function entrypoints, shared modules and canonical recipe/report contract tests. CI does not deploy to production.

## Protected production deployment

`.github/workflows/supabase-functions-deploy.yml` applies pending database migrations and deploys all 15 Edge Functions. It requires:

1. Dispatch from the `main` branch.
2. Input `confirm_production=DEPLOY`.
3. Required reviewer approval from the protected GitHub `supabase-production` environment.
4. GitHub environment secret `SUPABASE_ACCESS_TOKEN`.
5. GitHub environment secret `SUPABASE_DB_PASSWORD`.
6. GitHub environment variable `ADMIN_ORIGIN`.
7. The signing private key `RECIPE_SIGNING_PRIVATE_KEY_PEM` already present in Supabase Edge Function Secrets.

All Supabase CLI commands execute from `admin-panel/`, where `supabase/config.toml`, migrations and function source live. The workflow verifies signing-secret presence without reading its value, syncs `ADMIN_ORIGIN`, previews pending migrations, applies them, deploys all functions with JWT verification enabled and verifies migration history.

The workflow never transports the signing private key through GitHub. Supabase injects `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` into the Edge Function runtime; those reserved values are not stored in Render or Android.

## Remaining acceptance gates

The Render preview is live, but production Realtime migration, Edge Function release, RSA key provisioning, owner account bootstrap, real owner/member/device end-to-end tests and physical-device acceptance remain necessary. A green CI build alone is not production readiness.
