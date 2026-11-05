# Setup and deployment

## 1. Prerequisites

- Node.js 20.9+ and npm
- Supabase project with Auth, PostgreSQL and Edge Functions enabled
- Vercel or another Next.js-capable host
- Supabase CLI
- OpenSSL

## 2. Install the web app

```bash
npm install
cp .env.example .env.local
```

Set only these browser-safe values in `.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://shwqsljicmhflnvjqjah.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
NEXT_PUBLIC_APP_URL=http://localhost:3000
```

Do not add `SUPABASE_SERVICE_ROLE_KEY` or `RECIPE_SIGNING_PRIVATE_KEY_PEM` to `.env.local`, Vercel client variables or any `NEXT_PUBLIC_*` variable.

## 3. Apply the database migration

```bash
supabase link --project-ref shwqsljicmhflnvjqjah
supabase db push
```

Create the owner Auth user in Supabase Auth using a verified email. Then run the commented one-time owner bootstrap SQL from the migration with your exact owner email. The profile trigger always creates a member; it never promotes a user to owner automatically. Verify `profiles.role='owner'` and `status='active'` before signing in.

## 4. Generate an RSA key pair

Generate a 3072-bit RSA key pair (PKCS#8 private key):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out recipe-signing-private.pem
openssl pkey -in recipe-signing-private.pem -pubout -out recipe-signing-public.pem
```

Store the private key only in Supabase Edge Function secrets. The public key goes in the Android APK as an application resource / pinned constant; never send the private key to Android.

```bash
supabase secrets set --project-ref shwqsljicmhflnvjqjah \
  SUPABASE_URL=https://YOUR_PROJECT.supabase.co \
  SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY \
  SUPABASE_SERVICE_ROLE_KEY=YOUR_SERVICE_ROLE_KEY \
  RECIPE_SIGNING_PRIVATE_KEY_PEM="$(cat recipe-signing-private.pem)" \
  ADMIN_ORIGIN=https://YOUR_ADMIN_DOMAIN
```

Use the CLI's secret input method appropriate for your shell; avoid echoing secrets into CI logs. Delete temporary private key copies after securely backing them up offline.

## 5. Deploy Edge Functions

```bash
supabase functions deploy admin-invite-member
supabase functions deploy admin-manage-member
supabase functions deploy admin-upsert-tool
supabase functions deploy admin-publish-tool
supabase functions deploy admin-disable-tool
supabase functions deploy admin-assign-tool
supabase functions deploy admin-save-validation
supabase functions deploy admin-delete-draft-tool
supabase functions deploy admin-manage-device
supabase functions deploy admin-manage-tool-state
supabase functions deploy register-device
supabase functions deploy get-my-repair-tools
supabase functions deploy get-signed-repair-tool
supabase functions deploy submit-repair-run
supabase functions deploy get-admin-analytics
```

## 6. Run and deploy Next.js

```bash
npm run dev
npm run typecheck
npm run lint
npm test
npm run build
```

Set the same three public environment variables in the web host. Deploy the Next.js app. Configure the exact admin origin in the Edge Function secret `ADMIN_ORIGIN`; add localhost only in development.

## 7. Initial release

1. Sign in using the owner account.
2. Create a draft tool and validate the JSON DSL.
3. Publish through `admin-publish-tool` (for a production UI, wire this to a confirmation button and show the returned version ID).
4. Confirm the version row exists and is immutable.
5. Fetch via a member test account; verify the signature inside a debug Android build before enabling recipe execution.

No real-device test is implied by the Test Lab simulation.


## 8. GitHub Actions deployment

The repository includes a manual-only production Edge Function deployment workflow.

1. In GitHub, create an environment named `supabase-production`.
2. Add a required reviewer to that environment so a production deployment cannot run without approval.
3. Add the Supabase personal access token as the environment secret `SUPABASE_ACCESS_TOKEN`.
4. Merge the reviewed changes to `main`.
5. Open Actions → Deploy Supabase Edge Functions, select the `main` branch, and enter `DEPLOY` in the confirmation input.

The workflow deploys the 15 Edge Functions to project `shwqsljicmhflnvjqjah` and leaves JWT verification enabled. It does not apply database migrations or change Edge Function secrets. Migration changes and secret rotations remain separate, explicitly reviewed operations.

The workflow pins Supabase CLI `2.122.1`. Supabase CLI v3 changed hosted-command defaults and confirmation behavior; do not replace the pin with `latest` without adapting and verifying the deployment commands first.
