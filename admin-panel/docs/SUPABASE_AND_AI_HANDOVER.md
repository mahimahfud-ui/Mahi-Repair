# Mahi Repair Supabase and AI handover

## Canonical architecture

- Android member client: Kotlin/Jetpack Compose in `app/`.
- Separate Owner website: Next.js in `admin-panel/`.
- Shared repair identity, catalogue, signed recipes, device registration and reports: Supabase project `shwqsljicmhflnvjqjah`.
- AI recipe drafting: the existing MahiCloud Supabase Edge Function `mahi-ai-chat` in project `husydmiujigyonlxbuuj`.

The Android app has no Admin route, button or Admin screen entry. Owner management exists only in the separate web console.

## Owner PIN

The login form accepts an 8–12 digit PIN and authenticates the existing Owner Auth identity with Supabase `signInWithPassword`. Configure that existing Owner Auth account's password as the chosen numeric PIN in Supabase Auth before first use. Do not create a second Owner, change profile roles, or put the PIN in source code/environment files.

Every protected page, Server Action and AI route checks the authenticated Supabase user and the active `profiles.role = 'owner'` record. The configured Owner email is server-only as `OWNER_EMAIL`.

## AI providers

The Admin AI assistant calls MahiCloud's existing `mahi-ai-chat` Edge Function from a server route. A separate anonymous session is stored in an HttpOnly cookie; the Admin request is independently gated by the Mahi Repair Owner profile. Only the MahiCloud publishable key is used in the Admin server route. Provider API credentials remain in MahiCloud Supabase Edge Function secrets and never enter the browser, Android APK, GitHub source, or the Mahi Repair Supabase project.

The assistant is draft-only. It cannot create/publish a database record or execute Android actions. Its response must be checked in the Admin Test Lab against `RepairRecipeSchema` before an Owner explicitly publishes it.

Required Admin environment values are documented in `.env.example`. The two publishable keys are public client identifiers, not service-role keys.

## Android build configuration

The root `.env.example` contains the live repair Supabase URL, publishable key and public recipe-verification key. Copy it to `.env` when building locally. Never add Supabase service-role credentials or the recipe-signing private key to the Android build.

## Acceptance checks

1. Owner PIN login succeeds only for the existing active Owner.
2. A Member or suspended profile is denied by the Admin website and `/api/ai`.
3. Admin AI can stream a reply; an AI failure is shown without losing the rest of the console.
4. AI output is not written to `repair_tools` until the Owner reviews and publishes it.
5. Android app contains no Owner Admin menu/route and remains a member repair client.
6. Android unit tests, Admin typecheck/lint/Vitest/Next build, Supabase Edge Function checks and a physical-device smoke test pass before production release.

A passing CI build is not proof of a live Owner login, recipe signature match, published catalogue, or physical-device execution.
