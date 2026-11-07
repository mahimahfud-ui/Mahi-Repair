
# Mahi Repair — production readiness

Updated: 2026-11-07
Candidate branch: integration/mahi-repair-release-candidate
Candidate head: 65f31853db6c8c6d10d1299cefc564da12781b24

This checklist separates source implementation and CI evidence from acceptance in the actual production Cloud Run, Supabase and Android environments. Passing CI is not a production acceptance test.

## Implemented in the candidate

- [x] Separate native Android client, private Next.js Owner Admin Panel and Supabase backend.
- [x] Android home screen with a single RUN REPAIR action and published-tool picker.
- [x] Invite-only Android member email OTP implementation; account creation disabled.
- [x] Android Keystore-backed encrypted session storage and serialized refresh-token rotation implementation.
- [x] Server-issued device UUID and server-side member/device authorization.
- [x] Canonical recipe v1 schema, strict parameter validation and shared contract fixtures.
- [x] Canonical JSON serialization and RSA-SHA256 detached signing implementation.
- [x] Android signature verification and fresh online authorization before execution.
- [x] Native action allowlist; no remote scripts, shell commands, DEX, JAR, APK or arbitrary code execution.
- [x] Private per-member/per-device Realtime Broadcast source and Android foreground listener; events are opaque invalidation hints only.
- [x] Durable Android report outbox, immutable request JSON and retry worker.
- [x] Server-side idempotent report RPC with stable report UUID and request fingerprint.
- [x] Supabase migrations for RLS, private Realtime, report idempotency, atomic catalogue/device transactions and admin audit.
- [x] Candidate migration adds composite index device_tool_syncs(tool_id, version), removing the redundant tool_id-only index.
- [x] Local database tests cover Owner/Member RLS, direct-write denial, RPC privileges, report retry/conflict and index shape.
- [x] Candidate Auth Send Email Hook source verifies Standard Webhooks signatures and has Resend primary / Brevo fallback contract tests.
- [x] Protected production workflow source is manual and requires main-branch confirmation / protected environment; no production deploy was run.

## Latest candidate CI status

Exact current candidate SHA: 65f31853db6c8c6d10d1299cefc564da12781b24.

- [ ] Android CI #42359332992 — unit tests and debug APK assembly passed; Compose emulator test is still running. Source/APK scan and artifact upload are pending the Compose gate.
- [x] Admin Panel CI #42359333024 — dependency/security audit, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build passed.
- [x] Supabase Edge Functions CI #42359332985 — all candidate Edge Function entrypoints, Deno type checks and contract/source tests passed.
- [x] Supabase Database CI #42359332983 — all eight candidate migrations applied to a fresh ephemeral local Supabase stack; all 18 pgTAP assertions passed. The log reports All tests successful.

Links:
- Android: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332992
- Admin: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359333024
- Edge Functions: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332985
- Database: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332983

The previously uploaded debug APK artifact #13567644352 is from code commit 3207acb104a1ee390ba0a9fa621ae234d5bcfd01, not the current index-migration commit. It is debug-signed with an ephemeral CI key and is not a production release APK/AAB.

## Production configuration still required

- [ ] Generate a real RSA-3072 keypair. Keep only the PKCS#8 private key in Supabase Edge Function Secrets as RECIPE_SIGNING_PRIVATE_KEY_PEM; configure only the matching X.509 public key in Android release build configuration. The checked-in public key remains a placeholder.
- [ ] Provision the production Cloud Run Admin Panel service and record its canonical HTTPS origin.
- [ ] Configure the Cloud Run server-only ADMIN_OWNER_EMAIL and bootstrap the existing Owner Auth account with a strong random 8–12 digit PIN/password. Verify profile role=owner and status=active.
- [ ] Set the exact HTTPS ADMIN_ORIGIN in Supabase Edge Function Secrets.
- [ ] Configure the Supabase production site URL and exact Admin /auth/callback redirect allow-list.
- [ ] Configure and verify Resend and Brevo sender domains, SPF/DKIM/DMARC, API secrets and AUTH_EMAIL_FROM.
- [ ] Deploy send-auth-email with its signature-only JWT exception, generate SEND_EMAIL_HOOK_SECRET and configure it directly in Supabase Edge Function Secrets.
- [ ] Activate the Send Email Auth Hook only after provider inbox tests; send both Android member OTP and Admin/member invitation tests.
- [ ] Enable Supabase Auth leaked-password protection and review token/password rate limits.
- [ ] Review candidate production migration 20261107091600_add_device_tool_syncs_tool_version_index.sql through the protected workflow; it has not been applied to production.
- [ ] Keep production Realtime private and validate member/owner/revoked-device policy behavior after deployment.
- [ ] Configure protected GitHub production environment secrets/variables and required reviewer.
- [ ] Generate a separate Android upload keystore and produce a release-signed APK/AAB. Do not use the ephemeral CI debug key as a release key.

## End-to-end acceptance still required

- [ ] Owner login on the production Cloud Run HTTPS origin with the PIN.
- [ ] Confirm a Member account is denied entry to Owner Admin routes.
- [ ] Invite a Member and receive/verify the six-digit OTP using the live Auth email hook.
- [ ] Register a physical Android device and verify ownership binding and duplicate registration behavior.
- [ ] Create a repair tool, validate the recipe, publish it and verify the immutable signed version in Supabase.
- [ ] Verify private per-device Realtime event delivery; payloads must contain no recipe and only trigger authenticated refetch.
- [ ] Install the release-signed APK on a physical Android device and sign in as the invited Member.
- [ ] Verify the authorized catalogue, exact canonical UTF-8 payload and detached RSA signature before execution.
- [ ] Execute the supported native diagnostic actions with valid/invalid parameters and permission/confirmation cases.
- [ ] Submit a report and verify it appears in Admin.
- [ ] Disable/unpublish a tool and verify online retrieval/execution is denied.
- [ ] Revoke a device and verify server authorization is denied even if local cached data remains.
- [ ] Test refresh-token expiry/rotation, network interruption/reconnect, missed/duplicate events, suspended Member and multiple devices.
- [ ] Complete physical-device compatibility, offline/reconnect and update-install acceptance.
- [ ] Establish key rotation/revocation, backup/restore, telemetry privacy/retention and operational recovery procedures.

## Live project snapshot

Production Supabase project shwqsljicmhflnvjqjah reports ACTIVE_HEALTHY, region ap-south-1, PostgreSQL 17. Seven migrations are currently applied, nine core public tables have RLS enabled, and fifteen application Edge Functions are active with verify_jwt=true.

Production currently has one active Owner and one active Member, but zero devices, tools, signed versions, assignments, device sync rows and repair runs. The Member catalogue is empty. The production send-auth-email function and Auth Hook are not yet active.

The live Performance Advisor reports the unindexed device_tool_syncs(tool_id, version) foreign key; candidate migration fixes this, but has not been applied to production. The live Security Advisor reports leaked-password protection disabled.

## Staging and hosting limitations

The Primary Supabase organization is Free plan. Database branching was blocked because Pro is required; a quoted branch cost was $0.01344/hour. A separate free project was blocked by the two-active-Free-project limit. No project was paused/deleted and no plan upgraded. GitHub database CI is an ephemeral local stack and is not hosted staging.

The existing Render service mahi-repair-admin-preview still tracks security-hardening/run-repair and is an old preview, not the current production Admin Panel. The deployment guide specifies Google Cloud Run. No Cloud Run production service or health check is verified.

## Security acceptance boundaries

- Source/APK scans are marker scans for known privileged-secret patterns; they do not replace a full secret review or runtime inspection.
- Deno type checks and contract tests do not prove production Edge Function deployment or live Auth Hook activation.
- A Realtime event is not authorization. Every catalogue fetch and execution must be reauthorized by the server.
- Revocation cannot be observed by an offline device until it reconnects; the next online request must be denied.
- The debug APK artifact is not a release-signed package.
- Remote recipes may combine only capabilities already compiled into the installed APK. New native capabilities or Android permissions require an app update.

## Release decision

NOT PRODUCTION READY. Three candidate workflow suites pass on the current commit, but Android Compose CI is still running. Even after all CI is green, RSA signing, live email delivery, Cloud Run, leaked-password protection, production migration approval, release signing, physical-device testing and the complete Owner/Member/device/repair/report journey remain outstanding. Keep PR #8 draft and do not merge or deploy until the required gates have evidence and approval.
