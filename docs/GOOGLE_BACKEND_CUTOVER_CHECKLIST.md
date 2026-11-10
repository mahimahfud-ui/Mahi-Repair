# Google Backend Cutover — Acceptance Checklist

Run this checklist against the deployed Firebase/Cloud Run environment. Attach actual CI run links, emulator output, API test results and physical-device evidence to the migration PR. Do not mark a row complete from a screenshot or an AI Studio preview alone.

## A. Infrastructure and resource reuse

- [ ] Firebase project is the existing `manifest-hawk-76tvw`, unless the Owner explicitly approves a different project.
- [ ] Existing Firestore database ID and location are verified in the Firebase/Google Cloud console; no second database was silently created.
- [ ] Firestore indexes, TTL policies (if required), Auth providers, App Check, Cloud Run services and Secret Manager secrets are documented.
- [ ] Billing/paid services were not activated without explicit approval.
- [ ] Production, staging and local/debug configuration are separated.
- [ ] Cloud Run health/readiness endpoints work and disclose no secrets or personal data.

## B. Identity and role separation

- [ ] `mahinmhfd@gmail.com` authenticates with a real Firebase Auth flow and resolves to an active Owner profile.
- [ ] `mahimahfud@gmail.com` authenticates as Member and remains Member.
- [ ] No other user can self-register or self-promote to Owner.
- [ ] Owner bootstrap is one-time, server-only, audited and cannot be triggered by request parameters.
- [ ] Missing, malformed, expired, revoked or invalid Firebase ID tokens are rejected.
- [ ] Suspended/revoked Owner and Member profiles are denied.
- [ ] Member is denied from the Admin website and every Owner API, even when calling endpoints directly.
- [ ] Invite flow creates Member accounts only.
- [ ] Browser and Android clients cannot modify role/status/profile privilege fields.

## C. Firestore and App Check security

- [ ] Firestore rules default-deny and have no broad authenticated write rule.
- [ ] Users can read only their own profile and authorized device/tool/run data.
- [ ] Members cannot list all profiles, devices, tool drafts, assignments, versions or audit records.
- [ ] Only server-side Cloud Run/Admin SDK performs privileged writes.
- [ ] Rule tests run in Firebase Emulator Suite for anonymous, Owner, Member, suspended and cross-user cases.
- [ ] App Check is configured separately for debug and production and enforced on supported callable/client surfaces.
- [ ] App Check failure and token replay/invalid-token behavior are tested.
- [ ] Rate limits, bounded payload size, pagination limits and abuse handling are implemented.

## D. Admin website

- [ ] Responsive Admin website builds from canonical `admin-panel/` and uses the shared Firebase backend.
- [ ] Owner login/callback/session refresh/sign-out use real Firebase Auth.
- [ ] Every server action and API route independently revalidates Owner role; a hidden UI tab is not authorization.
- [ ] Dashboard metrics come from persistent production data, not demo seeds.
- [ ] Member management supports invite, suspend, reactivate and revoke without exposing Owner creation.
- [ ] Device listing, consented hardware profile, last-seen state and revoke/restore controls work.
- [ ] Tool draft/editor, schema validation, test lab, publish, versions, disable/archive and assignment workflows work.
- [ ] Published versions are immutable; updates create a new version.
- [ ] Repair logs, analytics, audit history and live sync status show real server data.
- [ ] Loading, empty, error, expired-session and rate-limit states are usable on mobile and desktop.

## E. Android application

- [ ] Application ID remains `com.aistudio.mahirepair.qrzntk`.
- [ ] Existing upload signing identity is preserved; a previously installed build can be upgraded.
- [ ] Real Firebase Auth flow replaces Supabase OTP/session code and all local/fake authentication.
- [ ] Session persistence, refresh, expiry, sign-out and revoked-user behavior are tested.
- [ ] Member device registration uses a server-issued device ID and binds it to the verified Firebase UID.
- [ ] Device profile/telemetry contains only documented fields with explicit consent and field-availability metadata.
- [ ] App retrieves only tools authorized for that Member/device.
- [ ] Android verifies RSA-SHA256 signature over exact canonical bytes before parsing the recipe.
- [ ] Tampered payload, invalid signature, unsupported schema, unknown action, invalid parameters, unknown key ID and revoked access are rejected.
- [ ] Android executes only locally implemented allowlisted actions; no downloaded scripts/shell commands.
- [ ] Destructive/permission-dependent actions require foreground user confirmation.
- [ ] Live update notification triggers fetch + verification + compatibility check + clear update prompt.
- [ ] Background sync survives offline/network recovery without duplicate installs or reports.
- [ ] Repair-run result is acknowledged by the server and stored with an idempotency key.

## F. Publishing and signature security

- [ ] Recipe schema version is explicit and shared by website/backend/Android tests.
- [ ] Backend validates all tool metadata and recipe parameters before publishing.
- [ ] Canonical serialization has cross-language test fixtures (TypeScript and Kotlin).
- [ ] RSA private key exists only in Secret Manager and is never sent to clients.
- [ ] Android contains only the matching public verification key(s).
- [ ] Signature response includes algorithm, key ID and immutable version identity.
- [ ] Key rotation supports overlap/retirement and a documented recovery procedure.
- [ ] Concurrent publish attempts cannot overwrite or skip versions.
- [ ] Every privileged mutation creates an append-only audit event.

## G. Device, repair-run and realtime behavior

- [ ] A real Android device registers against the correct Member and persists its server-issued ID.
- [ ] Device profile refresh updates only fields actually available on that device.
- [ ] Owner can revoke a device; its subsequent API calls/sync/execution authorization are rejected.
- [ ] Publishing/assigning/disabling a tool reaches the correct device(s) through authorized realtime/FCM flow.
- [ ] The app checks the signed catalogue and prompts before installing an update.
- [ ] Run submission persists once even when the client retries due to a timeout.
- [ ] Owner can see real run status, timestamps, step outcomes and device association.
- [ ] Telemetry retention, export and deletion behavior are documented.

## H. Remote support

- [ ] Remote support is a separately authenticated capability, not an assumed consequence of device enrollment.
- [ ] Owner must create a short-lived pairing session and the Member must visibly approve it on the Android device.
- [ ] Pairing codes are random, single-use, rate-limited and expire server-side.
- [ ] Session signaling is authorized by participant UID/device ID and does not expose reusable secrets.
- [ ] Screen capture uses Android's visible user-granted MediaProjection consent.
- [ ] Input-control capabilities, where implemented, are disclosed and separately granted; no stealth control or bypass of Android permission prompts.
- [ ] Either participant can terminate the session; Owner termination works immediately.
- [ ] Session creation, approval, start, end and failures are audit logged.
- [ ] A real Android-to-owner-browser/laptop test proves the actual transport. A UI mock or database status alone does not count.

## I. Migration, deployment and rollback

- [ ] Source and target record counts are reconciled; duplicate accounts and role changes are explained.
- [ ] Existing Supabase data is preserved during migration and the legacy project is not deleted prematurely.
- [ ] No user is mapped by email alone without verified re-authentication/identity proof.
- [ ] Secrets scan is clean for source, APK, web bundle, build logs and artifacts.
- [ ] Android CI, Admin Panel CI and Backend CI all pass on the exact migration commit.
- [ ] Firebase Emulator rules tests and Cloud Run API integration tests pass.
- [ ] Production Admin and Android have been tested with the two real accounts and a physical device.
- [ ] Render Admin and Supabase remain available until the Google cutover passes and rollback is documented.
- [ ] Rollback switches clients/routes back without deleting or corrupting either backend.
- [ ] Deployment report lists exact tested commit SHA, service URLs, project/database/region, test evidence, known limitations and rollback steps.

## Release decision

**Do not cut over** while any of the following are true: Member can access Owner controls; an unauthenticated/Member caller can publish or mutate tools; a signing private key is exposed; the database is ephemeral or duplicated; Android executes unverified content; real device enrollment/sync/reporting is untested; or no tested rollback exists.
