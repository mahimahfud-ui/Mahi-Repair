# Production-readiness checklist

## Implemented in this starter
- [x] Next.js App Router owner-console scaffold and dark responsive shell
- [x] Supabase cookie SSR client and server-side owner gate
- [x] OTP magic-link sign-in with account creation disabled
- [x] PostgreSQL schema, indexes, triggers and RLS policies
- [x] Member/owner role model with manual owner bootstrap
- [x] Strict recipe DSL schema and action allowlist
- [x] Static test-lab validation and dry-run disclosure
- [x] Test history table and owner-only validation-history endpoint
- [x] Edge Function auth helpers, role checks, rate-limit RPC and audit helper
- [x] RSA-SHA256 signing and exact canonical payload storage
- [x] Signed catalog delivery and member repair-run submission endpoints
- [x] Atomic member assignment RPC and server-issued device registration
- [x] Device revocation/restore UI and server endpoint
- [x] Tool draft/testing/published/disabled/archived state transitions
- [x] Member email visibility and multi-select tool assignment controls
- [x] Repair-run validation against immutable recipe step IDs and device compatibility
- [x] Server-side analytics aggregation and repair log filters
- [x] Immutable published version trigger and atomic version commit RPC
- [x] Android API contract, security and deployment documentation

## Required before production
- [ ] Install dependencies and run typecheck, lint, tests and Next.js production build
- [ ] Apply migration to a non-production Supabase project and test every RLS policy with owner/member/anonymous JWTs
- [ ] Verify Edge Functions with expired JWTs, suspended accounts, members invoking admin routes, malformed bodies and rate-limit floods
- [ ] Add explicit confirmation dialogs and result/error feedback to destructive UI actions
- [ ] Test the publishing button and concurrent version conflict handling against a live Supabase project
- [ ] Add server-only UI actions for all admin functions and robust error boundaries
- [ ] Validate Android manifest/package visibility, permission disclosures, settings-screen compatibility and host policy against the actual APK executor
- [ ] Add Android Kotlin implementations matching the exact action schemas and verify RSA signatures before parsing
- [ ] Add public-key rotation, key ID, key revocation and offline key backup procedures
- [ ] Add real-device test matrix across Android API levels and manufacturers
- [ ] Add function observability, backup/restore rehearsal and incident-response procedure
- [ ] Review privacy notice, consent flow, retention and deletion policy for device/run telemetry
- [ ] Configure custom domain, TLS, SMTP, MFA, redirect allowlist and production rate limits

A scaffold passing local static checks is not automatically production-certified. Mark the above only after the corresponding evidence exists.
