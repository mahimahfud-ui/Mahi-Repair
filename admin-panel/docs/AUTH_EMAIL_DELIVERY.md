# Shared Supabase Auth email delivery

## Purpose

Mahi Repair Android and the Mahi Repair Admin Panel use the same Supabase project, so one project-level Send Email Auth Hook serves both clients. It sends Android's existing six-digit OTP and the Admin's existing Magic Link/callback using Resend first and Brevo only when Resend fails.

The Android request remains the same Supabase OTP request and verification flow. The Admin request remains Supabase signInWithOtp with its existing emailRedirectTo value. The hook uses the Auth-generated email action, OTP, token hash and redirect_to; it does not hardcode an Android or Admin URL, change the redirect allow list, or grant account access.

Owner/member authorization, profiles.role/status, RLS, the Admin callback's active-Owner check, Android member checks, device authorization and session handling remain unchanged.

## Delivery behavior

1. Resend is the primary provider.
2. Brevo is attempted only after Resend returns a non-success status or the request fails.
3. Supabase Auth gets success only after the selected provider accepts every message for the Auth event.
4. If both providers fail, the hook returns a retryable 503 response.
5. Resend requests use the Supabase webhook delivery ID as an idempotency key.

A provider may accept a message but lose its response before the hook sees it. In that edge case the Brevo fallback can produce a duplicate email. The duplicate contains the same single-use Auth token and grants no additional privileges.

The function verifies the Standard Webhooks signature and never logs recipients, OTP values, token hashes, verification URLs, webhook signatures, provider response bodies or API keys. Operational logs contain only action type, provider result/status and whether fallback was used.

## Sender setup

Create Resend and Brevo transactional-email accounts. Verify the same authentication sending domain and sender address independently in both providers. Configure SPF and DKIM records exactly as each provider specifies and publish a DMARC policy. Disable link rewriting/click tracking for the Auth sender because it can break one-time verification links.

Use a dedicated sender such as no-reply@auth.yourdomain.tld after it has been verified in both accounts.

## Supabase Edge Function Secrets

Enter provider credentials directly in Supabase Dashboard → Edge Functions → Secrets. Do not paste credentials into chat, Android, GitHub source code or browser-visible Render variables.

Required before deploying the function:
- RESEND_API_KEY — Resend API key.
- RESEND_FROM_EMAIL — verified Resend sender address.
- BREVO_API_KEY — Brevo transactional API key.
- BREVO_FROM_EMAIL — verified Brevo sender address.

Optional:
- RESEND_FROM_NAME — defaults to Mahi Repair.
- BREVO_FROM_NAME — defaults to Mahi Repair.
- AUTH_EMAIL_APP_NAME — defaults to Mahi Repair.

SEND_EMAIL_HOOK_SECRET is generated in the Supabase Auth Hooks form after the Edge Function has been deployed. Copy it from the form, add it as an Edge Function Secret before saving/creating the Hook, then activate the Hook. The protected deployment workflow does not require this generated secret because it does not exist until the Dashboard Hook is prepared.

SUPABASE_URL is injected by Supabase Edge Functions; do not create a custom secret for it.

## Deploy and enable the hook

The protected production workflow remains gated to main, an explicit DEPLOY input and the protected supabase-production environment. It checks required secret names without printing values, deploys the 15 application functions with JWT verification, then deploys send-auth-email with JWT verification disabled. The hook function verifies the Standard Webhooks signature itself.

Prepare and enable the hook in this order:

1. Verify the sending domain and sender address in both Resend and Brevo.
2. Add RESEND_API_KEY, RESEND_FROM_EMAIL, BREVO_API_KEY and BREVO_FROM_EMAIL directly in Supabase Edge Function Secrets.
3. Run the protected production workflow from main with its required reviewer approval and DEPLOY input. It verifies provider secret names and deploys the signed hook endpoint with JWT verification disabled. The hook is not registered in Auth yet, so no Auth emails use it at this point.
4. Open Supabase Dashboard → Authentication → Hooks. Start a new Send Email hook, choose HTTP and set the endpoint to https://shwqsljicmhflnvjqjah.supabase.co/functions/v1/send-auth-email.
5. Click Generate Secret and copy the full v1,whsec_... value, but do not click Create/Save yet.
6. Add that value as SEND_EMAIL_HOOK_SECRET directly in Supabase Edge Function Secrets.
7. Return to the hook form and click Create/Save to activate it. Confirm the hook is enabled and the Email provider remains enabled under Authentication → Sign In / Providers.

The HTTP hook is a project-level Auth setting; enabling it routes both Android and Admin email events through this shared service. Local config.toml records verify_jwt = false for the signed HTTP hook endpoint but does not enable the hosted Auth Hook. Supabase Auth limits HTTP Hook request bodies to 20 KB; the handler enforces that limit.

## Acceptance tests after activation

- Admin Owner sign-in: receive a link, open it and complete the existing /auth/callback flow.
- Android invited-member sign-in: receive the six-digit code and verify it with the existing /auth/v1/verify type=email flow.
- Admin member invitation: use the existing invitation redirect and confirm the new account remains a Member.
- Resend failure in a non-production test: confirm Brevo accepts the same Auth-generated message.
- Both providers unavailable: confirm Auth gets a retryable failure rather than a false success.
- Confirm delivered messages arrive in the inbox; provider API acceptance alone is not proof of inbox delivery.

## Rollback

Disable the hook under Authentication → Hooks to return Auth to its configured SMTP sender. If no custom SMTP is configured, the default Supabase sender remains heavily rate-limited. Keep the function and hook secret available until the hook is disabled. Rotate the secret on the Dashboard hook and Edge Function secret together.
