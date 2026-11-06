# Google AI Studio Web App / Cloud Run deployment

## Keep the existing Next.js application

This is a monorepo. The deployable Admin Panel is the existing application in `admin-panel/`; it is not the root Vite prototype and it is not part of the Android APK.

Keep GitHub as the source of truth. Do not regenerate the Admin Panel from a prompt, replace it with a newly scaffolded AI Studio React app, or deploy the repository root. The Admin Panel already has its own Next.js routes, Server Actions, Supabase SSR session handling, recipe editor and Owner console.

Google AI Studio Build Mode can import an existing project from GitHub and deploy web apps to Cloud Run. For this repository, preserve the `admin-panel/` working directory and its existing Next.js structure. If the imported AI Studio workspace does not retain the monorepo subdirectory as its app/build root, use the checked-in Dockerfile and deploy from `admin-panel/` directly rather than allowing the root prototype to be published. See Google's [Build apps in AI Studio](https://ai.google.dev/gemini-api/docs/aistudio-build-mode) and [Deploying from AI Studio](https://ai.google.dev/gemini-api/docs/aistudio-deploying) guides.

## Runtime contract

- Node.js image: `node:22.23.4-bookworm-slim`.
- Install: `npm ci`.
- Build: `npm run build`.
- Next.js output mode: `standalone`.
- Start: `node server.js` (also exposed as `npm start`).
- Listen interface: `0.0.0.0`.
- Listen port: `process.env.PORT`, with container default 8080.
- Container user: unprivileged `nextjs`.
- Public probe: `GET /api/health`.

The Docker build context is this directory. `.dockerignore` excludes local environment files, dependencies, build output and Git metadata. The container does not need Supabase service credentials or the RSA signing private key.

## Preferred deploy workflow

From a shell authenticated to the Google Cloud project, deploy the existing app by using the Dockerfile:

```bash
cd admin-panel

gcloud run deploy mahi-repair-admin \
  --source . \
  --region asia-southeast1 \
  --port 8080 \
  --allow-unauthenticated \
  --set-env-vars="NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co,NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_PROJECT_KEY,ADMIN_OWNER_EMAIL=the-existing-owner-auth-email@example.com"
```

Cloud Run's source deployment uses a Dockerfile when one exists in the source directory. This command therefore builds the checked-in standalone image rather than guessing a build command from the monorepo root. Refer to [Deploy services from source code](https://docs.cloud.google.com/run/docs/deploying-source-code).

Use the Cloud Run console's environment settings instead of command-line variables when preferred. `ADMIN_OWNER_EMAIL` is server-only; never add the `NEXT_PUBLIC_` prefix. The two `NEXT_PUBLIC_SUPABASE_*` values are publishable project configuration, not privileged credentials.

## Google AI Studio Build Mode path

For a workspace that should be editable in AI Studio:

1. Open AI Studio Build Mode and choose **Import from GitHub** from the add-files menu.
2. Select the existing `mahimahfud-ui/Mahi-Repair` repository. Do not create a replacement app or repository.
3. Keep the existing Admin Panel code under `admin-panel/`; do not ask the agent to regenerate it.
4. Confirm the preview/build is running the Next.js application and that the deploy root points to `admin-panel/`.
5. Publish to Cloud Run only when the Build Mode workspace is demonstrably building this subdirectory and its existing Dockerfile/configuration.

If the Build Mode import cannot deploy a monorepo subdirectory as its app root, use the direct Cloud Run command above. This avoids publishing the unrelated root React prototype and keeps the Admin Panel code and deployment configuration in GitHub.

## Cloud Run environment variables

| Variable | Purpose |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL (publishable project configuration) |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Supabase publishable key |
| `ADMIN_OWNER_EMAIL` | Server-only Auth email for the one existing active Owner account |

The server reads these settings dynamically at runtime. Do not set `SUPABASE_SERVICE_ROLE_KEY`, a Supabase secret key, a database password or `RECIPE_SIGNING_PRIVATE_KEY_PEM` on the Cloud Run service.

## PIN authentication and secrets

The login screen accepts an 8–12 digit numeric PIN. Internally, the server uses the non-public `ADMIN_OWNER_EMAIL` value as the identifier for the existing Supabase Auth Owner user and submits the PIN as that user's password. The browser never receives the email identifier, and the PIN is not stored by the Admin Panel. Supabase Auth handles password hashing and verification.

Before sign-in can work:

1. Confirm the existing Owner Auth account is present and its database profile is `role = 'owner'`, `status = 'active'`.
2. Set that Auth user's password to a randomly generated 8–12 digit PIN using trusted Supabase Auth administration. Do not use a birthday, phone number, repeated digits or a sequence.
3. Set `ADMIN_OWNER_EMAIL` on Cloud Run to that exact existing Auth email.
4. Review Supabase Authentication → Rate Limits. Password sign-ins use the token endpoint's IP-based rate limit; configure a stricter limit appropriate for this single-Owner console.

No Owner magic-link callback, OTP email or SMTP delivery is part of the PIN login flow. Member invitation emails are a separate feature and continue to use the existing invitation flow if enabled.

Cloud Run must serve the public PIN page, so HTTP ingress is public. Only Supabase-authenticated users with an active Owner profile can reach the console. Every protected Server Action checks Owner authorization again before calling its allowlisted Edge Function.

Set Supabase Edge Function secret `ADMIN_ORIGIN` to the exact Admin Panel HTTPS origin. Keep `RECIPE_SIGNING_PRIVATE_KEY_PEM` only in Supabase Edge Function Secrets.

The Admin Panel uses the public Supabase publishable key and the signed-in user's session. Edge Functions keep their server-side service credentials and perform their own Owner authorization. Never move those credentials into Cloud Run.

## Post-deploy smoke tests

```bash
curl -i https://YOUR_SERVICE_URL/api/health
curl -i https://YOUR_SERVICE_URL/login
curl -i https://YOUR_SERVICE_URL/
```

Expected:

- `/api/health`: HTTP 200 with only `{"status":"ok"}` after all three required settings are configured.
- `/login`: the numeric Owner PIN form loads; no email field or email-link instructions are shown.
- Anonymous `/`: redirects to `/login`.
- A valid active Owner PIN establishes the normal Supabase SSR session.
- A valid member account or a non-active Owner cannot access the console.
- Administrative mutations use authenticated Edge Functions; failures are not translated into fake success.

Run the Admin Panel CI checks and complete Owner/member acceptance testing before using this service for production administration.
