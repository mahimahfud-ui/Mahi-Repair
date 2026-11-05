# Mahi Repair CI

Two independent GitHub Actions workflows run on every branch push, pull requests targeting `main`, and manual dispatch:

- Android: `.github/workflows/android-ci.yml`
- Admin Panel: `.github/workflows/admin-panel-ci.yml`

## Android workflow

The Android source has Gradle wrapper properties but does not contain the wrapper launcher or wrapper JAR. CI installs Gradle 9.3.1 using the official Gradle setup action instead of calling a missing `./gradlew`.

The build uses Android SDK Platform 36.1 (matching `compileSdk { version = release(36) { minorApiLevel = 1 } }`) and Build Tools 36.0.0. It generates a temporary debug keystore on the runner, then runs:

```bash
gradle --no-daemon --stacktrace testDebugUnitTest assembleDebug
```

On success, the debug APK is available from the run's `mahi-repair-debug-apk` artifact for 14 days. It is a debug build, not a release-signed APK. Unit-test reports are uploaded separately when available.

## Admin Panel workflow

The Admin Panel does not yet have a committed npm lockfile. CI disables npm cache and upgrades the runner's bundled npm 10 to npm 11 before dependency installation. It runs:

```bash
npm install
npm run check:scaffold
npm run typecheck
npm run lint
npm run test
npm run build
```

Next.js receives fake public Supabase configuration only. No live Supabase credentials are required for this build check.

**Recommended follow-up:** Generate and commit `admin-panel/package-lock.json` after a successful dependency resolution, then switch CI to `npm ci` and enable npm caching.

## Failure reporting

Each workflow writes an Actions job summary with overall status, individual step outcomes, commit SHA and a direct run-log link. Failed steps also produce GitHub Actions annotations. GitHub repository watchers receive native workflow failure notifications according to their own notification settings.

## Credentials and signing

These workflows do not reference repository secrets. They do not use Supabase service-role keys, RSA recipe-signing private keys, production API keys, Android release keystores or release passwords. The temporary debug keystore is created on the runner and is not committed or uploaded.
