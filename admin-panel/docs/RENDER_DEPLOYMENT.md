# Render deployment — Mahi Repair Admin Panel

## Current preview service

The Next.js owner console runs as a Render **Node.js Web Service**, not a Static Site.

- Service: `mahi-repair-admin-preview`
- URL: https://mahi-repair-admin-preview.onrender.com
- Dashboard: https://dashboard.render.com/web/srv-dbmdt10u01pc73a97osg
- Repository: `mahimahfud-ui/Mahi-Repair`
- Branch: `security-hardening/run-repair`
- Plan: Free preview
- Region: Singapore
- Runtime: Node.js **22.23.4**
- Build: `cd admin-panel && npm ci && npm run build`
- Start: `cd admin-panel && npm start`

The repository `.node-version`, `admin-panel/.nvmrc`, package engine and Render `NODE_VERSION` are all pinned to Node 22.23.4. Production should be a separate service created after review and merge; do not deploy the PR branch to the production hostname.

## Environment variables

Only browser-safe values belong in Render:

| Variable | Value |
|---|---|
| `NODE_VERSION` | `22.23.4` |
| `NEXT_PUBLIC_SUPABASE_URL` | `https://shwqsljicmhflnvjqjah.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Current project's publishable key |
| `NEXT_PUBLIC_APP_URL` | Exact Render HTTPS origin, no trailing slash |

Never put a service-role key, database password or recipe-signing private key in Render or in a `NEXT_PUBLIC_*` value.

## Supabase Auth and Edge Function origin

- Set Supabase Auth Site URL to the deployed Admin Panel origin.
- Add the exact `<origin>/auth/callback` URL to Supabase Auth redirect URLs.
- Set the custom Edge Function secret `ADMIN_ORIGIN` to that exact origin, without a path or trailing slash.
- The protected production workflow sets Supabase's `ADMIN_ORIGIN` custom secret from the GitHub `supabase-production` environment variable.

Supabase automatically injects `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` into Edge Functions. The `SUPABASE_` prefix is reserved; do not create custom secrets with that prefix.

## Signing key

Create a matching RSA-3072 key pair on a trusted machine. Add the PKCS#8 private key directly in Supabase Dashboard → Edge Function Secrets as `RECIPE_SIGNING_PRIVATE_KEY_PEM`. Do not store it in Render, GitHub, Android, source control or browser variables. Embed only the matching X.509 public PEM in Android. The checked-in Android public-key value remains a placeholder until the real pair is configured.

## Private Realtime and Android catalogue delivery

Android subscribes only while foregrounded to a private topic scoped to its authenticated member and registered device. Events contain only an opaque event ID. On an event the app calls `get-my-repair-tools`, verifies each signed canonical payload, and updates Room. App launch, resume, reconnect and WorkManager sync recover missed events.

Before production:

1. Review and apply `20261106120000_private_realtime_catalog_broadcast.sql`.
2. Disable Supabase Realtime **Allow public access**.
3. Verify `realtime.messages` RLS limits each authenticated session to its own registered-device topic.
4. Deploy all updated Edge Functions with JWT verification enabled.
5. Test publish, update, disable, assignment removal, member suspension and device revocation.
6. Verify a revoked device cannot fetch or execute tools even though it can receive its own opaque revocation signal.

The migration and Edge Function changes on this PR branch are not live in production until the protected Supabase workflow succeeds. A Render deploy does not deploy database migrations or Edge Functions.

## Preview smoke test

- `/login` displays the owner sign-in page.
- Unauthenticated requests to `/`, `/tools` and `/publishing` require authentication.
- The Supabase Auth callback resolves on the actual Render origin.
- No privileged secrets are present in Render variables or browser-visible bundles.
- Production publishing is enabled only after the signing secret, migration and updated Edge Functions are configured.
