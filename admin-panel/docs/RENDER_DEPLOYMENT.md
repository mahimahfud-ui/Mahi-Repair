# Render deployment — Mahi Repair Admin Panel

## Target

Deploy only the existing Next.js application in `admin-panel/` as a Render **Web Service**. Do not use a Static Site. The PR branch is a preview/staging deployment, not the production release.

The repository root contains `render.yaml`. It defines:

- Runtime: Node.js (the app's `.nvmrc` selects Node 22; `package.json` requires Node 20.9+).
- Root directory: `admin-panel`.
- Build: `npm ci && npm run build`.
- Start: `npm start` (the existing `next start` script; Render's assigned `PORT` is used by Next.js).
- Branch: `security-hardening/run-repair`.
- Automatic deploy: only after the linked GitHub checks pass.
- Region: Singapore.
- Plan: Free for this preview. No paid plan is selected by this configuration.

## Create the preview service

1. In the intended Render workspace, create a Blueprint using this repository and the `security-hardening/run-repair` branch.
2. Confirm the Blueprint points to the root `render.yaml` and inspect the proposed resource before applying it.
3. Create the `mahi-repair-admin-preview` Web Service. Do not create a Static Site.
4. Wait for the first deploy to complete, then copy the service's actual assigned origin from Render. Never infer the URL from the service name.

## Environment variables

Set these three browser-safe variables on the Render service:

| Variable | Value |
| --- | --- |
| `NEXT_PUBLIC_SUPABASE_URL` | `https://shwqsljicmhflnvjqjah.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | The current project's Supabase publishable key |
| `NEXT_PUBLIC_APP_URL` | The exact origin assigned by Render, including `https://` and no trailing slash |

Set the variables before the production build. Redeploy after changing a value that Next.js embeds into the client bundle.

**Never add** `SUPABASE_SERVICE_ROLE_KEY`, the database password, or `RECIPE_SIGNING_PRIVATE_KEY_PEM` to Render or to any `NEXT_PUBLIC_*` variable. They belong only in the Supabase Edge Function secret store.

## Supabase origin and authentication configuration

After Render has provided the real URL:

1. Set the Supabase Edge Function secret `ADMIN_ORIGIN` to the exact Render origin. Keep the value to one origin (scheme + host), without a path or trailing slash.
2. In Supabase Auth URL Configuration, set the Site URL to the deployed origin and add the exact callback URL `<actual-origin>/auth/callback` to the redirect allow list.
3. Confirm `NEXT_PUBLIC_APP_URL` and `ADMIN_ORIGIN` match the same verified origin.
4. Do not deploy production Edge Functions or apply production migrations as a side effect of creating the Render preview.

## Live Android synchronization prerequisites

The app listens only while foregrounded on an authenticated private Broadcast topic scoped to its member and registered device. It reacts to a minimal event by calling `get-my-repair-tools`; it does not receive recipe JSON over Realtime. Every catalogue entry is signature-verified before it reaches Room, and execution fetches a fresh signed release through `get-signed-repair-tool`.

Before enabling this branch against a live Supabase project:

- Review and apply the migration `20261106120000_private_realtime_catalog_broadcast.sql`.
- In the Supabase Realtime settings, disable **Allow public access** so clients must use private channels.
- Deploy the updated `get-my-repair-tools`, `get-signed-repair-tool`, and `submit-repair-run` Edge Functions with JWT verification enabled.
- Verify the active device/member RLS policy and test publication, update, disable, assignment removal, member suspension, and device revocation.
- Confirm that a missed event is recovered by the full sync performed after reconnect/foreground and by WorkManager's periodic sync.

The migration and Edge Function changes in this PR are source changes only until a separately reviewed Supabase deployment is completed. Do not use a Render deploy as proof that these backend prerequisites are live.

## Preview acceptance check

- Render service type is Web Service, not Static Site.
- The deployment SHA is the reviewed PR head.
- `/auth/callback` resolves on the actual Render origin.
- Owner authentication works against the intended Supabase project after `ADMIN_ORIGIN` and Auth URL settings are updated.
- No privileged service-role or private signing key is present in Render environment variables or browser-visible bundles.
- Admin publish/update/disable operations succeed only after the matching Edge Functions and migration are deployed.
- Android receives only an event notification, then fetches its own authorized, signed catalogue.
