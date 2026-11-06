# Mahi Repair CI and release gates

The repository uses GitHub Actions only; no paid CI service is required.

## Workflow inventory

- Android build and test: `.github/workflows/android-ci.yml`
- Admin Panel verification: `.github/workflows/admin-panel-ci.yml`
- Supabase Edge Function and migration verification: `.github/workflows/supabase-functions-ci.yml`
- Protected production deploy: `.github/workflows/supabase-functions-deploy.yml`
- Dependency update automation: `.github/dependabot.yml`

The Android and Admin Panel workflows run for pushes and pull requests targeting both `main` and `security-hardening/run-repair`. Supabase CI uses path filters for Edge Functions, migrations, Supabase configuration, shared contract fixtures, CI scripts and the relevant workflow files. Migration-only changes therefore trigger Supabase CI. All PR workflows use read-only repository permissions and cancel superseded runs for the same PR/ref.

External GitHub Actions are pinned to reviewed commit SHAs. Dependabot checks the GitHub Actions, npm and Gradle ecosystems weekly.

## Android CI

- Temurin JDK 17.
- Android SDK Platform 36.1, Build Tools 36.0.0 and Platform Tools.
- The checked-in Gradle wrapper controls the Gradle version (9.3.1); the CI setup action provides caching but does not replace the wrapper.
- XML/manifest validation confirms `android:allowBackup=false`, that both backup rule resources are actually referenced, and that every app-private domain is excluded from cloud backup and device transfer.
- Android unit tests, debug APK assembly and Compose UI tests on an API 35 Google APIs emulator.
- Tracked-source scanning and generated APK archive inspection for private-key PEM blocks, Supabase service-role JWTs/secret API keys and common provider tokens.
- Unit-test and Compose test reports plus the debug APK are retained as GitHub Actions artifacts for 14 days.

The workflow creates only an ephemeral conventional Android **debug** keystore because the app currently declares a custom debug signing config. It is used for the temporary CI debug APK, is not committed or uploaded, and is not a release-signing identity. The APK artifact is not a production release. Google AI Studio remains responsible for its separate installation/release-signing workflow.

## Admin Panel CI

- Exact Node.js 22.23.4, matching `.node-version` and `package.json`.
- `npm ci` consumes `admin-panel/package-lock.json`; this also fails on package/lockfile inconsistency.
- `npm audit --audit-level=moderate` blocks moderate, high and critical advisories.
- Repository source secret scan and the existing scaffold/security-integrity checks.
- TypeScript, ESLint, Vitest and the Next.js production build.
- Build-time Supabase values are public placeholders only. No production or service-role credentials are supplied.

## Supabase CI

- Exact inventory check for all 15 Edge Function directories and their `index.ts` entrypoints.
- Deno type-check for every entrypoint and shared module.
- Directory-wide Deno contract test discovery, including recipe and run-report contract tests.
- Exact expected SQL migration inventory, unique timestamp and non-empty source checks.
- Validation that the production deploy inventory matches the 15 functions, protected functions do not disable JWT verification, and production approval gates remain in place.
- Repository-wide privileged-secret scan.

The current checkpoint has a run-report test for signed step-ID authorization, but it does **not** yet contain a dedicated request-idempotency test or idempotency implementation in `submit-repair-run`. The CI command recursively discovers all Deno tests, so a dedicated test added by the Supabase implementation owner will run automatically. This pipeline change does not alter application/Edge Function source or invent idempotency behavior.

## Protected production deployment

`.github/workflows/supabase-functions-deploy.yml` is deliberately dispatch-only and applies changes only from `main` when `confirm_production` is exactly `DEPLOY`. It references the protected GitHub environment `supabase-production`; repository administrators must ensure that environment is configured with required reviewer approval in GitHub Settings.

The workflow requires environment secrets `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`, plus the exact canonical HTTPS origin in the `ADMIN_ORIGIN` environment variable. It links the configured Supabase project, checks that `RECIPE_SIGNING_PRIVATE_KEY_PEM` already exists in Supabase Edge Function Secrets without reading its value, previews migrations before applying them, deploys the exact protected function inventory with JWT verification left enabled, and checks migration history. It does not transport a private key or service-role key through repository files or workflow artifacts.

The Supabase service-role key is provided by the Supabase Edge Function runtime; it is not a required GitHub Actions secret for these CLI operations and must not be committed or stored as a normal repository variable.

## Remaining acceptance gates

A successful CI run does not prove production readiness. The protected GitHub environment's required-reviewer configuration, live Supabase credential provisioning, signing-key provisioning, owner bootstrap, live owner/member/device tests and physical Android-device acceptance remain external acceptance gates. Production deployment is not run by CI on pull requests.
