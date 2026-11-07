
# Mahi Repair — execution checkpoint

Updated: 2026-11-07

## Repository and release-candidate state

- Repository: https://github.com/mahimahfud-ui/Mahi-Repair
- Integration branch: integration/mahi-repair-release-candidate
- Candidate PR: https://github.com/mahimahfud-ui/Mahi-Repair/pull/8
- Current candidate head: 65f31853db6c8c6d10d1299cefc564da12781b24
- PR #8 targets security-hardening/run-repair at fe3af5f3d178315ee4cf0807cf2dab008965bfce.
- PR #8 remains OPEN, DRAFT and UNMERGED. Core PR #1 remains OPEN, DRAFT and UNMERGED.
- Do not merge to main, deploy production, apply production migrations, change production secrets, or modify production data without explicit approval.

## Latest CI evidence on the current candidate

The following workflow results are for the exact candidate head 65f31853db6c8c6d10d1299cefc564da12781b24:

| Workflow | Run | Result | Details |
|---|---:|---|---|
| Supabase Database CI | #42359332983 | PASS | Fresh local stack, all 8 tracked migrations, 18 pgTAP assertions; log reports All tests successful. |
| Supabase Edge Functions CI | #42359332985 | PASS | All candidate Edge Function entrypoints, Deno type checks and contract/source checks. |
| Admin Panel CI | #42359333024 | PASS | Dependency/security checks, scaffold checks, TypeScript, ESLint, Vitest and Next.js production build. |
| Android CI | #42359332992 | IN PROGRESS | Unit tests and debug APK assembly passed; Compose emulator test is the active step. APK scan and artifact upload wait for the Compose gate. |

Direct run links:
- Android: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332992
- Admin Panel: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359333024
- Supabase Edge Functions: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332985
- Supabase Database: https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42359332983

The Android CI path filter was updated so unrelated docs/database-only pushes do not repeatedly consume the emulator test job. This candidate commit itself includes the Android workflow change, so Android CI is running once on the current head.

## Recent Android test history and APK

The previous fully green Android run is #42356155144 on code commit 3207acb104a1ee390ba0a9fa621ae234d5bcfd01. It passed unit tests, assembleDebug, all 6/6 Compose UI tests, source credential scan, generated APK secret scan and artifact upload.

Artifact #13567644352 is available at:
https://github.com/mahimahfud-ui/Mahi-Repair/actions/runs/42356155144/artifacts/13567644352

The extracted app-debug.apk is 24,521,209 bytes and has SHA-256:
9e2a786b1683d6c4bfc6cb40adf85be9e4eb85586e8d3a31fa63dfe0f66e9aec

That artifact is debug-signed with an ephemeral CI key. It is suitable only for controlled smoke testing; it is not a production release-signed APK/AAB. A fresh artifact from current SHA 65f31853 is pending the current Android workflow.

Earlier Compose test fixes were test-selector corrections only: a dedicated tool-picker title tag removed an ambiguous text selector, and a tool assertion was corrected to use the actual server-tool-id. No visible UI behavior was changed.

## Candidate database change

Live Supabase Performance Advisor identified the missing composite index for the foreign key device_tool_syncs(tool_id, version).

The candidate now adds:
- Migration 20261107091600_add_device_tool_syncs_tool_version_index.sql
- Composite B-tree index device_tool_syncs_tool_version_idx on (tool_id, version)
- Removal of the redundant single-column device_tool_syncs_tool_id_idx, because the composite index covers tool_id lookups through its leftmost prefix.
- An additional pgTAP assertion confirming the composite index exists and the redundant old index is absent.

The latest database CI applies all eight candidate migrations to an ephemeral local Supabase stack and passes 18 checks. This migration has NOT been applied to production. The live database still has the prior seven migration records and the advisor warning remains there until a separately approved production deployment.

## Verified live Supabase state

- Project: Mahi-Repair, ref shwqsljicmhflnvjqjah, region ap-south-1, status ACTIVE_HEALTHY, PostgreSQL 17.
- Seven migrations are applied in the live production project.
- All nine core public application tables report RLS enabled.
- Fifteen application Edge Functions are active and all fifteen require verify_jwt=true.
- Eight critical deployed function sources previously matched the candidate source after normalizing packaged import paths.
- SECURITY DEFINER administrative/transactional RPCs inspected are executable by service_role, not anon or authenticated.
- Private Realtime access policies are implemented, but actual WebSocket delivery, reconnect and revocation behavior have not been accepted end-to-end.
- Live profile counts: one active Owner and one active Member.
- No registered devices, repair tools, signed versions, assignments, device sync rows, repair runs, audit rows or recipe-test history currently exist. The Member catalogue is empty until the Owner configures signing and publishes a real signed recipe.
- The candidate send-auth-email function is not deployed or activated in the live project.

## External production blockers

1. The root .env.example still contains a placeholder RECIPE_SIGNING_PUBLIC_KEY_PEM. Generate a matching RSA-3072 pair on a trusted machine. Store only the PKCS#8 private key in Supabase Edge Function Secrets; put only the matching X.509 public key into the Android build configuration.
2. The live Supabase Auth security advisor reports leaked-password protection disabled. Enable and verify it in the Supabase Dashboard.
3. The live Send Email Auth Hook is not deployed or enabled. Resend/Brevo sender domains, provider secrets, generated SEND_EMAIL_HOOK_SECRET, hook activation and inbox tests remain outstanding.
4. The real Admin Panel production origin and Google Cloud Run service are not provisioned/verified. The legacy Render preview still tracks the older security-hardening/run-repair branch and is not production.
5. A production Android upload keystore, release-signed APK/AAB and physical-device test are not available.
6. The complete live Owner → Member invitation/OTP → device registration → signed recipe publication → Android repair → report → Admin review workflow has not been tested.
7. A persistent hosted staging Supabase environment is blocked by the Primary organization Free-plan branch/project limits. The successful GitHub database workflow is ephemeral local CI, not hosted staging.

## Supabase plan and staging constraint

Primary Supabase organization is on the Free plan. A database branch was quoted at $0.01344/hour and could not be created because branching requires Pro. A separate $0 project was also blocked by the organization limit of two active Free projects. No project was paused/deleted and no subscription was upgraded. The live Mahi-Repair project was not modified.

## Separate Admin repository

Mahi-Repair-Admin PR #2 remains open and unmerged:
https://github.com/mahimahfud-ui/Mahi-Repair-Admin/pull/2

Its latest CI run #42358138564 passed. That repository contains a separate Auth email hook implementation. The integration PR #8 already contains its own reviewed hook implementation and passing Deno contract tests; the two implementations differ and have not been blindly combined.

## Resumption sequence

1. Poll Android run #42359332992 and verify Compose tests, APK scan and artifact upload.
2. Record the exact workflow IDs and artifact SHA for the latest head.
3. Keep PR #8 in draft while production signing, Auth email, Cloud Run, hosted staging and physical-device acceptance remain unresolved.
4. Obtain Owner-controlled secrets and release-signing configuration through secure dashboards, never through chat or source control.
5. Use the protected production workflow only after explicit approval and a reviewed preview.
6. Complete real Owner/member/device/recipe/report acceptance before calling the product production-ready.
