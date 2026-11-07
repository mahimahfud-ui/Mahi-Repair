
# Mahi Repair — acceptance report

Updated: 2026-11-07
Repository: https://github.com/mahimahfud-ui/Mahi-Repair
Integration PR: https://github.com/mahimahfud-ui/Mahi-Repair/pull/8
Candidate head: 65f31853db6c8c6d10d1299cefc564da12781b24

This report distinguishes source implementation, CI evidence, live Supabase inspection and real end-to-end acceptance. Implemented is not equivalent to verified.

## Summary

The candidate contains the Android repair client, Admin Panel, Supabase schema/functions, private Realtime and report idempotency implementation. On the current candidate head, Admin Panel, Edge Functions and database CI have passed. Android unit tests and APK assembly have passed; the Compose emulator test is still running. The current candidate's new composite-index migration passes on an ephemeral local Supabase database.

The project is NOT PRODUCTION READY. There is no registered device or published repair catalogue in the live database, recipe signing remains a placeholder, the Auth Send Email Hook is not activated, production Cloud Run and release signing are not configured, and no physical-device Owner/member/report journey has been accepted.

## Evidence matrix

| Area | Status | Evidence / limitation |
|---|---|---|
| Candidate PR/change control | VERIFIED | PR #8 is open, draft and unmerged, targeting security-hardening/run-repair. PR #1 is also open, draft and unmerged. |
| Android unit tests | VERIFIED (current head) | Run #42359332992 reached the Compose step after unit tests passed. |
| Android debug APK assembly | VERIFIED (current head) | Run #42359332992 reached Compose after assembleDebug succeeded. |
| Android Compose UI tests | IN PROGRESS | Run #42359332992 is still running the emulator Compose step. Do not mark passed until GitHub reports success. |
| Android source/APK credential scans and upload | PENDING | Current run waits for Compose completion before artifact scan/upload. Earlier code commit #3207acb passed these gates. |
| Previous Android Compose tests | VERIFIED | Run #42356155144 passed all 6/6 Compose tests, unit tests, debug APK assembly, source scan and generated APK scan. |
| Previous debug APK artifact | VERIFIED (debug only) | Artifact #13567644352; extracted APK SHA-256 9e2a786b1683d6c4bfc6cb40adf85be9e4eb85586e8d3a31fa63dfe0f66e9aec. Not production signed. |
| Admin Panel | VERIFIED (CI) | Current run #42359333024 passed dependencies/security, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build. |
| Supabase Edge Functions | VERIFIED (CI) | Current run #42359332985 passed all candidate entrypoints, Deno checks and contract/source tests. |
| Supabase Database | VERIFIED (ephemeral CI) | Current run #42359332983 applied all eight candidate migrations to a fresh local Supabase stack and passed all 18 pgTAP assertions. |
| Supabase live project | VERIFIED (health/schema) | Production project shwqsljicmhflnvjqjah reports ACTIVE_HEALTHY, PostgreSQL 17. |
| Live migration history | VERIFIED | Seven migrations are applied in production. The new eighth candidate migration is not applied to production. |
| Core public-table RLS | VERIFIED (configuration inspection) | All nine application tables have RLS enabled. Live cross-user runtime acceptance is not complete. |
| Live Edge Functions | VERIFIED (inventory) | Fifteen application functions are active and JWT-verified. send-auth-email is not deployed/activated. |
| Candidate Auth email hook | IMPLEMENTED / CI VERIFIED | Candidate source includes a Standard Webhooks-verified hook with Resend primary and Brevo fallback tests. Production sender secrets and Auth hook are not configured. |
| Report idempotency | IMPLEMENTED / DB CI VERIFIED | Server-side RPC and Android durable outbox are in candidate. Fresh DB tests cover first submit, exact retry, altered fingerprint conflict and one stored row. Live request/concurrency acceptance remains outstanding. |
| Composite device-sync index | IMPLEMENTED / DB CI VERIFIED | Candidate migration adds (tool_id, version), drops redundant tool_id-only index, and pgTAP asserts the desired indexes. Not applied to live production. |
| Private Realtime | IMPLEMENTED; runtime NOT TESTED | Private per-owner/per-device policy source exists; actual delivery, reconnect and revocation need live testing. |
| Admin Owner PIN | IMPLEMENTED; live login NOT TESTED | Admin login accepts an 8–12 digit PIN as the existing Owner Auth password and checks active Owner profile. Cloud Run/account bootstrap is not verified. |
| Security advisor | ACTION REQUIRED | Live Supabase Auth leaked-password protection is disabled. |
| RSA recipe signing | BLOCKED | Android public-key configuration is still a placeholder. Matching RSA-3072 keypair required. |
| Production hosting | NOT VERIFIED | Existing Render preview is old branch; Cloud Run production deployment is not provisioned or smoke-tested. |
| Live app data | BLOCKED FOR MEMBER USE | One active Owner and one active Member, but zero registered devices, repair tools, signed versions, assignments, sync rows and repair runs. |
| Physical Android device | NOT TESTED | Emulator CI does not prove a real-device install or behavior. |
| Release APK/AAB | BLOCKED | No production upload keystore or release signature is available. |
| Complete Owner → Member → device → repair → report journey | NOT TESTED | Requires signing, email, hosting, a physical device and published real test recipe. |
| Production merge/deployment | NOT AUTHORIZED | No PR merge, production migration, function deployment, Cloud Run deployment or production data modification was performed. |

## Current candidate CI references

- Android CI #42359332992: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332992
- Admin Panel CI #42359333024: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359333024
- Supabase Edge Functions CI #42359332985: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332985
- Supabase Database CI #42359332983: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332983

The database CI log confirms the new composite-index migration was applied and the pgTAP result was PASS / All tests successful. The workflow is an ephemeral local Supabase stack; it is not a hosted staging project.

## Live Supabase inspection

- Project status ACTIVE_HEALTHY; region ap-south-1; PostgreSQL 17.
- Seven migration-history entries are present in production. Candidate migration 20261107091600_add_device_tool_syncs_tool_version_index.sql is not yet applied live.
- Public tables: profiles, devices, repair_tools, repair_tool_versions, repair_tool_access, device_tool_syncs, repair_runs, admin_audit_logs, recipe_test_history. RLS is enabled on all nine.
- Fifteen production application Edge Functions are active and verify_jwt=true.
- Security-definer transactional/admin RPCs inspected are not executable by anon or authenticated; service_role has the required execute grants.
- The live profile state includes one active Owner and one active Member.
- Live data counts: profiles=2; devices=0; repair_tools=0; repair_tool_versions=0; repair_tool_access=0; device_tool_syncs=0; repair_runs=0; admin_audit_logs=0; recipe_test_history=0.
- Because there are no published repair tools or assigned recipes, the Member catalogue is empty until the Owner configures RSA signing and publishes a real signed recipe.
- Production send-auth-email is absent and the Supabase Auth Send Email Hook is not activated.

## Database performance/security findings

The live Performance Advisor reports the foreign key device_tool_syncs_tool_version_fk on (tool_id, version) has no covering index. Candidate migration 20261107091600 adds a composite index and removes the redundant single-column tool_id index. The same candidate has a pgTAP assertion for the index state.

The live Auth Security Advisor reports leaked-password protection disabled. Enable it in Supabase Auth settings. Other unused-index notices are informational; do not remove indexes blindly based on a low-volume empty database.

## External production gates

1. Generate RSA-3072 keypair on a trusted machine. Private PKCS#8 key only in Supabase Edge Function Secrets; matching X.509 public key only in Android build configuration.
2. Replace the placeholder public key and build a production release after the signing configuration is established.
3. Enable leaked-password protection and review Auth rate limits.
4. Verify Resend and Brevo sender domains, add provider secrets, deploy the reviewed send-auth-email function, generate SEND_EMAIL_HOOK_SECRET and activate the Supabase Send Email Hook only after inbox tests.
5. Provision Cloud Run Admin hosting, exact HTTPS origin, health check, Owner PIN bootstrap and Auth callback allow-list.
6. Establish persistent staging or an approved alternative. Current Supabase Free plan blocks database branches and a third Free project.
7. Install a release-signed APK on a physical Android device and complete the Owner → Member invitation/OTP → device registration → signed recipe publish → repair → report → Admin review journey.
8. Obtain explicit approval before any main merge or production deployment.

## Staging provisioning result

The Primary Supabase organization is on Free plan. A database branch was quoted at $0.01344/hour but was rejected because the plan does not support branching. A separate free project was blocked because the organization has two active Free projects. No project was paused/deleted and no plan was upgraded. No persistent staging database exists.

## Release decision

NOT PRODUCTION READY. Candidate Admin, Edge Function and database CI pass; Android Compose verification for the current head is still running. Even once Android CI is green, the signing key, Auth email activation, production hosting, release signing, live catalogue population, physical-device test and complete end-to-end journey remain unverified.
