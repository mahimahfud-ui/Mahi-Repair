# Build and verification status

Verification performed in the generated project workspace on 2026-11-05:

| Check | Result | Evidence / limitation |
|---|---|---|
| Static project inventory and security-integrity audit | PASS | `npm run check:scaffold` (runs with Node only) |
| TypeScript/TSX syntax parsing | PASS | 51 TypeScript/TSX files parsed with the globally available TypeScript parser; this is not a typecheck |
| Recipe JSON Schema syntax | PASS | Parsed as JSON by the scaffold audit |
| DSL action ID alignment | PASS | 13 IDs matched between frontend catalog, Edge validator, JSON Schema and Kotlin verifier source |
| RLS and direct-write restrictions | PASS (static inspection) | All 10 application tables enable RLS; browser roles have write grants revoked; policies are SELECT-only |
| Signed publication path | PASS (static inspection) | Schema validation → canonical JSON → RSA-SHA256 signing → transactional version RPC |
| Unpublished tool delivery guard | PASS (static inspection) | Catalog delivery intersects assignments with currently published tools |
| Browser secret scan | PASS (static inspection) | No service-role or RSA private-key environment access in `src/` |
| Dependency installation | BLOCKED | `npm install --ignore-scripts --no-audit --no-fund --prefer-offline` timed out; no `node_modules` or lockfile was produced |
| TypeScript typecheck, ESLint, Vitest and Next.js production build | NOT RUN | Dependencies could not be installed |
| Supabase migration / Edge Function integration tests | NOT RUN | No Supabase project credentials were supplied or connected |
| RSA signing / Android Kotlin build / native device execution | NOT RUN | No generated key pair, Android project, APK or test device was supplied |
| Hosting deployment | NOT RUN | No hosting project credentials or target repository was supplied |

Static checks are not equivalent to production acceptance. Complete the remaining steps in `PRODUCTION_READINESS.md` against a non-production Supabase project and the actual Android APK before deployment.
