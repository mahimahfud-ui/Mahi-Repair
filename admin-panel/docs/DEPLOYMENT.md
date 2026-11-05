# Setup and deployment

## Prerequisites

- Node.js 22 (the repository's `.nvmrc`); `package.json` requires Node.js 20.9+.
- Supabase project with Auth, PostgreSQL, Edge Functions and Realtime enabled.
- Supabase CLI and OpenSSL.
- Render account connected to the GitHub repository.

## Local setup

```bash
cd admin-panel
npm ci
cp .env.example .env.local
```

Configure only browser-safe values:

```env
NEXT_PUBLIC_SUPABASE_URL=https://shwqsljicmhflnvjqjah.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
NEXT_PUBLIC_APP_URL=http://localhost:3000
```

Never add `SUPABASE_SERVICE_ROLE_KEY`, the database password, or `RECIPE_SIGNING_PRIVATE_KEY_PEM` to `.env.local`, Render, browser variables, source control, or any `NEXT_PUBLIC_*` variable. These privileged values belong only in the Supabase Edge Function secret store.

## Official hosting: Render Web Service

The Admin Panel uses Next.js server-side functionality, authentication and server actions. Deploy it as a **Node.js Web Service**, not a Static Site.

The repository root contains `render.yaml` for a preview deployment from `security-hardening/run-repair`. It defines:

- Runtime: Node.js; `admin-panel/.nvmrc` selects Node 22.
- Root directory: `admin-panel`.
- Build command: `npm ci && npm run build`.
- Start command: `npm start`.
- Region: Singapore.
- Plan: Free preview plan.
- Auto deploy: wait until linked branch checks pass.

Use the [Render deployment guide](./RENDER_DEPLOYMENT.md) to create the Blueprint service. Render must assign the actual service URL. Do not infer or hardcode a URL from the service name.

Set these values in the service environment:

| Variable | Value |
| --- | --- |
| `NEXT_PUBLIC_SUPABASE_URL` | `https://shwqsljicmhflnvjqjah.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | The current project's publishable key |
| `NEXT_PUBLIC_APP_URL` | The exact origin assigned by Render, with no trailing slash |

After Render creates the service, copy its actual origin. Set `NEXT_PUBLIC_APP_URL` to that value, then rebuild/redeploy so Next.js receives it at build time. Set the Supabase Edge Function secret `ADMIN_ORIGIN` to the exact same origin. Also configure Supabase Auth Site URL and `<actual-origin>/auth/callback` in the redirect allow list.

Do not deploy the PR branch to the final production hostname before review and merge.

## Supabase database, signing and Edge Functions

Link the project:

```bash
supabase link --project-ref shwqsljicmhflnvjqjah
```

Generate a 3072-bit RSA key pair locally:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out recipe-signing-private.pem
openssl pkey -in recipe-signing-private.pem -pubout -out recipe-signing-public.pem
```

Keep the PKCS#8 private key exclusively in Supabase Edge Function secrets. Embed only the matching public key in the Android application. Never commit either private-key material or service-role secrets.

The following branch migrations and Edge Function changes are source code only until a separately reviewed production deployment is approved:

- `20261106120000_private_realtime_catalog_broadcast.sql`
- `get-my-repair-tools`
- `get-signed-repair-tool`
- `submit-repair-run`

Review and apply migrations separately. Deploy the Edge Functions with JWT verification enabled. Do not use a Render deployment to imply Supabase functions or migrations were deployed.

## Private Realtime setup

The Android client listens while foregrounded on the authenticated, private, per-member/per-device channel. Events contain only an opaque event ID; they never include recipe payloads. A received event triggers a full request to `get-my-repair-tools`. The client verifies every signed canonical payload before Room is updated and obtains a current signed version from `get-signed-repair-tool` before execution.

Before enabling this against production:

1. Review and apply the private Realtime migration.
2. Ensure Realtime's **Allow public access** setting is disabled.
3. Verify the `realtime.messages` RLS policy only authorizes the member's own active registered-device topic.
4. Deploy the three updated Edge Functions.
5. Verify access removal, member suspension, and device revocation on the server.

Reconnect/foreground performs a full sync; WorkManager performs periodic eventual-consistency sync when the process is not active. Private events are hints only and never authorize execution.

## Verification

```bash
cd admin-panel
npm run typecheck
npm run lint
npm test
npm run build
```

The Deno function tests and Android tests run through their GitHub Actions workflows. Real owner/member/device tests, end-to-end Realtime tests and physical-device tests require configured identities, valid signing keys and a test device. Test Lab or CI results are not a substitute for those tests.

See [Render deployment](./RENDER_DEPLOYMENT.md) for service configuration and production blockers.
