# Mahi Repair — Architecture Alignment, Production Operations and Release Gates

Status: **HOLD — operational runbook; not production certification**  
Applies to: Android app, Owner Admin console, backend APIs, device pairing, remote support and live updates.

This document covers production concerns that are not safely solved by UI implementation or a green CI build. It is deliberately isolated from application code so it does not conflict with the five parallel implementation agents.

## 1. Architecture conflict that must be resolved before production deployment

The repository's current implementation documentation and the latest user-directed target describe different architectures.

| Concern | Existing repository implementation/docs | User-directed target |
|---|---|---|
| Identity/authentication | Supabase Auth | Existing Firebase project / Firebase Auth integration |
| Primary database | Supabase PostgreSQL with RLS | Firestore |
| Realtime | Supabase private Realtime | Authenticated Cloud Run signaling / Firebase-supported realtime |
| Admin hosting | Next.js Admin Panel documented for Cloud Run; Render preview is described as retired | Existing Render Admin website as laptop console |
| Shared API/data | Supabase Edge Functions and Admin server actions | One Cloud Run API and one Firestore database shared by Android and Admin |

These are not interchangeable configuration details. The existing Android authentication/catalogue/realtime paths and the user-directed Firebase/Firestore target cannot both be treated as the single source of truth.

### Required decision and migration rules

1. The Owner's current target is the authoritative product requirement: one shared backend/data source for Android and the Admin console; no separate Admin database.
2. Before shipping, the implementation team must record the final architecture in an Architecture Decision Record (ADR), including the canonical identity provider, canonical database, API origin, realtime/signaling path and Admin hosting location.
3. Do not implement dual writes to Supabase and Firestore. Dual writes can produce inconsistent invitations, device ownership, revocation, repair history and support sessions.
4. If Firebase Auth + Firestore is selected as the final authority, implement a staged migration from existing Supabase data and sessions. Include identity mapping, data export/import, ownership verification, rollback and a cutover plan. Do not simply add Firebase dependencies and call the migration complete.
5. If Supabase remains the authority, explicitly obtain Owner approval to retain Supabase Auth/Postgres/Realtime and treat Cloud Run only as the Admin hosting/API layer where applicable. Do not describe Firebase Auth or Firestore as integrated.
6. Render hosting and Cloud Run hosting must not be conflated. The Admin website may be hosted on Render while calling the shared Cloud Run API, but this must be demonstrated by the actual deployment configuration. If the Admin is instead hosted on Cloud Run, update the product requirement and deployment documentation.
7. Every Android and Admin request must resolve identity, role, device ownership and session authorization against the same canonical backend.
8. Keep the previous system read-only during migration, then cut over only after parity tests pass. Retire old endpoints only after a verified rollback window.

**Release blocker:** no production deployment until the architecture decision, data ownership and deployment origin are unambiguous and verified from code/configuration.

## 2. Key and credential lifecycle

Mahi Repair has multiple cryptographic trust boundaries. Never reuse one key for unrelated purposes.

| Key/credential | Purpose | Storage rule | Rotation/recovery |
|---|---|---|---|
| Android APK signing key | Establishes app update identity | Dedicated secure signing system / protected CI secret store; never repository | Back up securely; loss can prevent in-place updates |
| Repair recipe signing private key | Signs owner-published repair recipes | Server-side secret manager only | Version key IDs, overlap old/new public keys during rotation, revoke compromised key |
| Recipe verification public key | Verifies signed recipes in Android | May be embedded in APK | Support planned key rollover and minimum app version |
| Backend service identity | Cloud Run-to-database access | Workload identity / secret manager; least privilege | Rotate/revoke independently |
| TURN credentials | WebRTC relay access | Short-lived credentials minted server-side | Short TTL; never hardcode long-lived TURN password in APK |
| Auth/session secrets | User and Owner sessions | Auth provider / secure server-side cookie or Android Keystore | Revoke sessions and rotate on compromise |
| Update manifest signing key | Authenticates update metadata/packages | Server-side secret manager only | Independent from recipe and APK signing keys |

Required controls:
- Document key owner, creation date, algorithm, key ID, storage location and rotation procedure without recording secret values.
- Generate private keys on a trusted machine or managed KMS; never paste private material into chat, issues, logs or GitHub.
- Verify public/private key matching before deployment without printing private key material.
- Use short-lived credentials for WebRTC TURN access and backend service-to-service calls.
- Audit all secret access and failed signature verification.
- Define emergency revocation and recovery before release.

## 3. Release engineering and Android update safety

### Release identity
- Fix the Android applicationId/package name and signing identity before the first public release.
- Use a production upload/release key distinct from CI debug keys.
- Keep versionCode strictly increasing.
- Verify every release APK/AAB is signed by the expected certificate.
- Preserve the signing key securely; a lost or changed key can prevent users from updating an installed sideloaded APK.
- Publish checksums and release provenance for every artifact.

### Update channel policy
- Separate remote configuration, declarative repair recipes/content packs and native APK updates.
- Only allow remote updates to data/configuration that the installed app already knows how to interpret.
- Do not deliver executable scripts, DEX/JAR files, dynamically loaded libraries or arbitrary code as a "small update".
- Sign the update manifest and verify package hashes/signatures before activation.
- Enforce package ID, signer identity, version compatibility and anti-downgrade policy.
- Download to a temporary location, verify fully, then atomically activate. Keep the previous valid configuration/package until the new one is confirmed.
- If native code or Android permissions change, use a legitimate signed Android package update and the platform installation flow.
- Support staged rollout: internal devices → small percentage → broader rollout.
- Provide pause, revoke and rollback controls. Rollback of native APKs must respect Android versionCode/signing constraints; do not promise unsupported silent downgrade.
- Record update attempt, package version, result and failure reason without collecting unnecessary personal data.

## 4. Monitoring, alerting and service health

A production system needs operational visibility, not only application logs.

Monitor at minimum:
- API availability and latency (p50/p95/p99).
- Authentication failures and abnormal login rate.
- Invitation creation, email delivery, bounce and provider fallback rates.
- Device registration and pairing failures.
- Remote-support request-to-consent conversion and session establishment failures.
- WebRTC ICE failure rate, TURN relay usage, connection duration and disconnect reason.
- Repair command authorization denials and signature verification failures.
- Update manifest fetch, download, verification, activation and rollback failures.
- Database errors, query latency, quota, storage and connection saturation.
- Cloud Run instance count, CPU, memory, restarts and 5xx responses.

Operational requirements:
- Provide a health endpoint that reports service readiness without exposing secrets, database contents or internal configuration.
- Use structured logs with request ID, session ID and device pseudonymous ID where needed.
- Never log access/refresh tokens, invitation tokens, OTPs, private keys, TURN passwords, full email bodies or screen contents.
- Configure alert thresholds and an on-call/Owner notification route for sustained failures.
- Set a retention period for logs and metrics.
- Test alert delivery before production launch.
- Keep health checks separate from deep dependency diagnostics; return only safe status information publicly.

## 5. Database backup and disaster recovery

Before production launch, define and record:
- Recovery Point Objective (RPO): maximum acceptable data loss.
- Recovery Time Objective (RTO): maximum acceptable restoration time.
- Automated backup schedule and retention.
- Encryption and access control for backups.
- Point-in-time recovery availability, if supported by the selected database plan.
- Backup coverage for database records, object/package storage and release manifests.
- Recovery owner and emergency access process.

Perform a restore rehearsal into an isolated environment:
1. Create a backup.
2. Restore to a clean isolated project/database.
3. Verify users, device ownership, invitations, audit records and release manifests.
4. Verify foreign keys, indexes, authorization policies and storage objects.
5. Run authentication, device authorization and update-manifest smoke tests.
6. Record duration and discrepancies.
7. Do not overwrite production during the rehearsal.

A backup that has never been restored is not proven recoverable.

## 6. Privacy, data minimization and retention

Create a data inventory before collecting device information.

For every field, document:
- What is collected.
- Why it is required.
- Whether it is optional.
- Whether user permission is required.
- Where it is stored.
- Who can access it.
- Retention period.
- Deletion/revocation behavior.

Default restrictions:
- Collect only device diagnostics required for the requested repair/support session.
- Do not collect contacts, messages, photos, personal files, call logs, unrelated app data or precise location unless a separate feature has a justified, explicit permission flow.
- Do not persist screen-share video by default. Live screen frames should be transient media, not silently recorded.
- Make screen sharing visible and stoppable by the Android user.
- Make support purpose, requesting Owner identity and session state visible before consent.
- Provide account/device revocation and data deletion/export procedures appropriate to the chosen backend.
- Define retention and deletion for device telemetry, remote-support audit events, invitation records, logs and update history.
- Ensure logs and analytics use pseudonymous IDs where possible.

## 7. Remote support incident controls

Implement and test operational kill switches:
- Disable new remote-support sessions globally.
- Disable remote support for one user or device.
- Revoke one active session immediately.
- Disable a repair tool or recipe release.
- Pause an update release.
- Revoke a compromised signing key.
- Force minimum supported app version only with a tested recovery path.

Incident procedure:
1. Stop the affected capability.
2. Revoke active sessions and credentials.
3. Preserve relevant audit events.
4. Identify affected accounts/devices/releases.
5. Notify the Owner through the configured channel.
6. Rotate compromised secrets/keys.
7. Restore from a verified backup if required.
8. Re-enable only after a regression/security test.
9. Record cause, impact, remediation and follow-up.

Every kill switch must be server-enforced. Hiding a button in the UI is not a security control.

## 8. Cost and capacity controls

Estimate and monitor:
- Cloud Run/API request volume and instance usage.
- Database reads/writes and storage.
- Email provider volume and fallback usage.
- TURN relay bandwidth and duration.
- Update package storage and download egress.
- Logs and monitoring ingestion.

Use:
- Rate limits per account/device/session.
- Maximum remote-support session duration and idle timeout.
- TURN credential TTL and relay quotas.
- Bounded update package size and download retry/backoff.
- Database indexes based on observed query patterns.
- Budget alerts and service quotas.

Do not promise that every update will be 8–9 MB. Report actual package size and update type to the user.

## 9. Production acceptance matrix

Production acceptance must use a real Owner browser session and at least one physical Android device. A second physical device is required for multi-device and revocation tests.

| Test | Evidence required |
|---|---|
| Owner login | Successful authorized session; Member denied |
| Invitation | Real email received, accepted, expired/revoked cases rejected |
| Device registration | Server-owned ID and correct Owner association |
| QR pairing | One-time token, expiry and replay rejection |
| Device inventory | Admin shows actual device data and last-seen status |
| Remote support | User approves; real live WebRTC media reaches laptop |
| Consent revocation | Android stop action terminates session and further access |
| Repair authorization | Signed allowlisted command only; invalid/expired/replayed command rejected |
| Audit | Session and command events persisted with actor/device/session IDs |
| Update | Valid manifest/package verified and applied; corrupt/invalid package rejected |
| Rollback | Failed config/content update returns to previous known-good state |
| Native update | Correct signer/package identity and Android installation confirmation |
| Offline recovery | Reconnect restores state without duplicate actions |
| Device revocation | Subsequent online requests denied; active session terminated |
| Backup restore | Isolated restore passes integrity and authorization smoke tests |
| Monitoring | Test alert is delivered and actionable |

For every test, record build SHA, backend release/version, device model/Android version, timestamp, expected result, actual result and evidence link. Redact tokens and personal data.

## 10. Go-live decision

Do not call the product production-ready until all of these are true:

- [ ] Architecture ADR approved and code/configuration match it.
- [ ] One canonical identity provider and database are established; no uncontrolled dual-write path exists.
- [ ] Production HTTPS origins, CORS/redirect allowlists and service identity are verified.
- [ ] Owner-only authorization and device ownership are tested against the live backend.
- [ ] Email delivery is tested in real inboxes, including provider fallback.
- [ ] Production signing keys are provisioned, backed up and documented.
- [ ] Android release package is signed with the permanent release identity.
- [ ] Remote support consent, WebRTC transport and immediate termination pass on real devices.
- [ ] Update packages and manifests are signed and rollback-tested.
- [ ] Monitoring and alerts are active.
- [ ] Backup/restore rehearsal is successful.
- [ ] Privacy inventory, retention and deletion procedures are approved.
- [ ] Incident kill switches are tested.
- [ ] Final production acceptance evidence is attached to the release.

**Current release posture:** HOLD until these gates have fresh evidence. A successful CI build, a debug APK or a simulated WebRTC test is not production acceptance.
