# Render deployment — Mahi Repair Admin Panel

## Preview service configuration

The checked-in `render.yaml` describes the Free preview service, not a production service.

- Service: `mahi-repair-admin-preview`
- URL: https://mahi-repair-admin-preview.onrender.com
- Dashboard: https://dashboard.render.com/web/srv-dbmdt10u01pc73a97osg
- Repository: `mahimahfud-ui/Mahi-Repair`
- Configured branch: `ai/mahi-repair-complete-workflow`
- Plan: Free preview
- Region: Singapore
- Runtime: Node.js 22.23.4
- Build: `cd admin-panel && npm ci && npm run build`
- Start: `cd admin-panel && npm start`
- Health check: `GET /api/health`

The branch field was aligned to the Agent 3 working branch so a Render Blueprint sync uses this code line. A GitHub push does not prove that Render has synchronized the Blueprint or completed a deploy.

## Deployment verification boundary

The execution environment could not access the public Render preview endpoints during this task. The Render service, latest deployed commit, TLS state, owner login and production health therefore remain unverified. Do not report this preview as deployed or production-ready until a Render deploy event and live smoke test have been observed.

Once the Blueprint is synchronized, verify:

```bash
curl -i https://mahi-repair-admin-preview.onrender.com/api/health
curl -i https://mahi-repair-admin-preview.onrender.com/login
curl -i https://mahi-repair-admin-preview.onrender.com/
```

Expected responses:
- `/api/health`: `200 {"status":"ok"}` when the required public Supabase URL and publishable key are configured; otherwise `503 {"status":"not_ready"}`.
- `/login`: Owner email sign-in form loads.
- Anonymous `/`: redirects to `/login`.
- An active Owner can complete the email link flow and access the console.
- A member or suspended account is signed out and returned to `/login?denied=1`.

## Environment variables

Only public project configuration belongs in Render:

| Variable | Purpose |
|---|---|
| `NODE_VERSION` | `22.23.4` |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Publishable key |
| `NEXT_PUBLIC_APP_URL` | Exact deployed HTTPS origin, without a trailing path |

Never put the service-role/secret key, database password or `RECIPE_SIGNING_PRIVATE_KEY_PEM` in Render or in any `NEXT_PUBLIC_*` variable.

## Supabase Auth and Edge Function origin

- Set the hosted Supabase Auth Site URL to the final Admin Panel origin.
- Add the exact `<origin>/auth/callback` URL to the hosted Auth redirect allow list.
- Set Edge Function secret `ADMIN_ORIGIN` to the exact HTTPS origin.
- Verify the hosted Auth Session settings: use a 12-hour session timebox and eight-hour inactivity timeout (or an explicitly approved policy).
- The `[auth.sessions]` values in `supabase/config.toml` apply to local Supabase configuration; the hosted project must be configured separately in the Supabase Dashboard.

## Signing and deployment secrets

Create a matching RSA-3072 key pair on a trusted machine. Add the PKCS#8 private key directly in Supabase Dashboard → Edge Function Secrets as `RECIPE_SIGNING_PRIVATE_KEY_PEM`. Do not store it in Render, GitHub, Android, source control or browser variables. Embed only the matching X.509 public PEM in Android release build configuration.

A Render deployment does not apply Supabase migrations or deploy Edge Functions. Use the protected production workflow and its required environment reviewers/secrets for those operations.

## Private Realtime and device catalogue

Android currently subscribes only to a private member/device topic for opaque catalogue-change events. The event prompts a fresh authorized recipe fetch; it is not a remote support or screen-sharing channel.

Before production:
1. Review/apply the private Realtime migration using the protected production workflow.
2. Disable Supabase Realtime public access.
3. Verify RLS allows a member to receive only their own active registered-device topic and rejects revoked devices.
4. Deploy the updated Edge Functions with JWT verification enabled.
5. Test publish/update/disable/unpublish, assignment removal, member suspension and device revocation.

The Android Realtime channel does not provide WebRTC signaling or a live screen stream.

## Production deployment procedure

1. Create a separate Render Node.js Web Service from the approved production branch/commit.
2. Use the Node 22.23.4 runtime and existing build/start commands.
3. Configure only the public Supabase values and exact production origin.
4. Verify the deploy event, deployed commit, Render health and HTTPS login.
5. Set hosted Supabase Auth Site URL/redirect allow list, Session timebox/inactivity and Edge Function `ADMIN_ORIGIN`.
6. Test an active Owner, a member, an inactive account, logout and access from an expired session.
7. Keep all privileged secrets in Supabase Edge Function Secrets.
8. Record the Render service ID, URL, deployed commit and smoke-test evidence in the release record.

Do not publish a production URL until the production service has actually been created and tested.
