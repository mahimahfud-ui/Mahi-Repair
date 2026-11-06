# Mahi Repair — setup and deployment

## Production architecture

- `app/`: native Android repair client.
- `admin-panel/`: separate private Next.js Owner Admin Panel.
- Supabase: shared Auth, PostgreSQL with RLS, private Realtime and authenticated Edge Functions.
- Google Cloud Run: production hosting for the Admin Panel.

The Android APK does not contain Admin Panel routes, URLs, Owner credentials or Owner-only actions. Render is retired as a hosting target.

## Local development

The Admin Panel is pinned to Node.js **22.23.4**.

```bash
cd admin-panel
npm ci
cp .env.example .env.local
npm run dev
```

Set the following values in `.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_PROJECT_KEY
ADMIN_OWNER_EMAIL=the-existing-owner-auth-email@example.com
```

The URL and publishable key are public project configuration. `ADMIN_OWNER_EMAIL` is server-only and must not use the `NEXT_PUBLIC_` prefix. Never add a Supabase service-role/secret key, database password or recipe-signing private key to this application.

## Owner PIN authentication and security boundaries

- The login page asks for an 8–12 digit numeric Owner PIN; it does not ask the Owner to type an email or request a magic link/OTP.
- `ADMIN_OWNER_EMAIL` identifies the one existing Supabase Auth account on the server. The PIN is that account's Supabase Auth password; the app never stores the PIN in source, environment variables, browser storage or logs.
- Set a random 8–12 digit password for the existing Owner Auth user in Supabase Auth administration. Keep the corresponding `profiles` row set to `role = 'owner'` and `status = 'active'`. Do not use a birthday, phone number, repeated digits or a predictable sequence.
- The server action calls Supabase `signInWithPassword`, then verifies the returned user against the active Owner profile before redirecting to the console. Failed and non-Owner logins receive a generic denial.
- Supabase Auth's token endpoint rate limits password sign-in attempts by IP. Review and tighten the token endpoint limits in Supabase Authentication → Rate Limits before production. A short numeric PIN is not suitable; use the full 8–12 digits.
- Auth cookies use `HttpOnly`, `SameSite=Lax`, `Path=/`, and `Secure` in production.
- Every protected console request repeats the Owner check in the server layout.
- Every privileged Server Action independently calls `requireOwner()` and invokes an allowlisted authenticated Supabase Edge Function.
- Role or status values from form data and user-editable metadata are never trusted.

The Owner email remains an internal Supabase Auth account identifier only; it is not shown in the login UI and no sign-in email is sent. Member invitation emails are a separate feature and continue to use the existing invitation flow.

Public Cloud Run ingress is required so the Owner can load the PIN screen. It does not grant console access. The Admin Panel itself enforces Supabase authentication and the active Owner profile on protected pages/actions.

## Deploy to Cloud Run

Build and deploy with the repository's Dockerfile. **Run the command from `admin-panel/`**, so the build context contains only the Next.js app and does not include Android sources or the root legacy React preview.

```bash
cd admin-panel

gcloud run deploy mahi-repair-admin \
  --source . \
  --region asia-southeast1 \
  --port 8080 \
  --allow-unauthenticated \
  --set-env-vars="NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co,NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_PROJECT_KEY,ADMIN_OWNER_EMAIL=the-existing-owner-auth-email@example.com"
```

Cloud Run source deployment uses the Dockerfile when one is present in the source directory. The image pins Node.js **22.23.4**, builds Next.js standalone output, runs as an unprivileged user and binds to `0.0.0.0` on the Cloud Run `PORT` (defaulted to 8080 in the image). See Google's [Cloud Run source deployment documentation](https://docs.cloud.google.com/run/docs/deploying-source-code).

The `/api/health` endpoint returns only `{ "status": "ok" }` when the Supabase URL, publishable key and server-side Owner Auth email are configured. Otherwise it returns `{ "status": "not_ready" }` with HTTP 503. It never returns settings, credentials, database records or user information.

The image can also be built with Docker/Cloud Build and deployed as a prebuilt image when a controlled CI/CD pipeline is preferred.

## Supabase production configuration

1. Ensure the Supabase Email Auth provider supports password sign-in for the existing Owner account. No magic-link/OTP sign-in or SMTP delivery is needed for Owner login.
2. Set the existing Owner user's Auth password to a random 8–12 digit PIN, and set `ADMIN_OWNER_EMAIL` in Cloud Run to that user's Auth email.
3. Verify that the matching `profiles` row has `role = 'owner'` and `status = 'active'`.
4. Review Supabase Authentication → Rate Limits and lower the token endpoint limit as appropriate for a single-Owner console.
5. Set the custom Edge Function secret `ADMIN_ORIGIN` to the exact Admin Panel HTTPS origin.

Supabase Edge Functions automatically receive their reserved runtime variables, including the service-role key. Do not copy those values to Cloud Run. Member invitations remain a separate email feature; configure delivery only if you use that feature.

## Recipe signing key

The PKCS#8 RSA-3072 private signing key belongs only in Supabase Dashboard → Edge Function Secrets as `RECIPE_SIGNING_PRIVATE_KEY_PEM`. Only the matching X.509 public key belongs in Android release configuration. Never put the private key in Cloud Run, the Admin Panel, browser variables, GitHub or the APK.

## Release verification

```bash
cd admin-panel
npm ci
npm run check:scaffold
npm run typecheck
npm run lint
npm run test
npm run build
```

After deployment, verify:

- `GET /api/health` is status-only.
- Anonymous `GET /` redirects to `/login`.
- The PIN screen loads and an active Owner can sign in using the configured PIN.
- A member account cannot enter the console, even if it is otherwise a valid Supabase user.
- Draft create/edit and recipe validation persist through Supabase.
- Publish creates a signed immutable version; unpublish/disable and rollback call the corresponding Edge Functions.
- Assignments, invitations, member suspension/reactivation, device revocation, repair reports and audit information show persisted Supabase data.
- An Edge Function error or no-row update never displays success.

A successful image build or health response does not replace an end-to-end Owner/member acceptance test.
