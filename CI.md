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
- Android unit tests, debug APK assembly and Compose UI tests on an API 35 Google APIs Pixel 2 emulator. The emulator boot allowance is 900 seconds; spellchecker and system animations are disabled to reduce flaky emulator startup/UI behavior.
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

- Exact allowlist for all 15 Edge Function directories and their index.ts entrypoints.
- The seven reviewed migration filenames are explicitly allowlisted. Every migration must be non-empty and start with a unique 14-digit timestamp. Adding, removing or renaming a migration requires an intentional review and allowlist update; the check does not silently accept arbitrary SQL files.
- While PR #5 remains separate, the workflow overlays only its reviewed backend function/migration paths from commit 83a050413cff9f15a3fd0f881ff3e978287e533d into the ephemeral CI runner when the target tree lacks the final migration. This does not merge or modify either branch. Once the reviewed migration inventory exists in the target tree, the overlay is skipped.
- Deno type-checking covers all 15 entrypoints, shared modules and every recursively discovered *_test.ts file.
- Deno tests run with read access limited to contract-tests/, admin-panel/supabase/migrations/ and admin-panel/supabase/functions/. The current combined tree has seven test files:
  - admin_audit_contract_test.ts — static checks for owner-only transactional audit SQL.
  - database_integrity_contract_test.ts — static checks for report-idempotency and relational-integrity SQL.
  - http_contract_test.ts — source-level checks for exact-origin CORS behavior.
  - member_catalog_contract_test.ts — static checks for registration and catalog SQL contracts.
  - realtime_contract_test.ts — static checks for private Realtime policy, event payload and invalidation triggers.
  - recipe_test.ts — shared recipe fixture/schema/canonicalization test.
  - run_report_test.ts — report step authorization, first-submit/identical-retry/conflict outcomes, fingerprint stability and fail-closed RPC error mapping.
- Python CI tests prove that the production deployment guard rejects an actual Supabase functions deploy command containing --no-verify-jwt while ignoring comments and echo statements.
- Repository-wide privileged-secret scanning runs against the combined source tree.

The five *_contract_test.ts files that inspect SQL migrations are static source-contract checks. They read migration text and assert expected SQL constructs; they do not execute migrations and do not prove actual PostgreSQL parsing, transaction behavior, RLS enforcement, Realtime authorization, concurrency safety or deployed Supabase behavior. The run-report Deno tests exercise TypeScript helper contracts and fingerprints, while database idempotency/atomicity assertions remain static SQL checks. Real PostgreSQL/Supabase migration, RLS, Realtime, transaction, concurrency and end-to-end tests remain a separate pre-production acceptance gate.

## Protected production deployment

`.github/workflows/supabase-functions-deploy.yml` is deliberately dispatch-only and applies changes only from `main` when `confirm_production` is exactly `DEPLOY`. It references the protected GitHub environment `supabase-production`; repository administrators must ensure that environment is configured with required reviewer approval in GitHub Settings.

The workflow requires environment secrets `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`, plus the exact canonical HTTPS origin in the `ADMIN_ORIGIN` environment variable. It links the configured Supabase project, checks that `RECIPE_SIGNING_PRIVATE_KEY_PEM` already exists in Supabase Edge Function Secrets without reading its value, previews migrations before applying them, deploys the exact protected function inventory with JWT verification left enabled, and checks migration history. It does not transport a private key or service-role key through repository files or workflow artifacts.

The Supabase service-role key is provided by the Supabase Edge Function runtime; it is not a required GitHub Actions secret for these CLI operations and must not be committed or stored as a normal repository variable.

## Remaining acceptance gates

A successful CI run does not prove production readiness. The protected GitHub environment's required-reviewer configuration, live Supabase credential provisioning, signing-key provisioning, owner bootstrap, live owner/member/device tests and physical Android-device acceptance remain external acceptance gates. Production deployment is not run by CI on pull requests.
