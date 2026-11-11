# Mahi Repair — Production Backend Setup & Handover Checklist

This guide provides the exact sequential steps for the **Owner** to configure the live Supabase project (`shwqsljicmhflnvjqjah`) and connect the deployed Owner Admin Panel (`https://mahi-repair-admin.onrender.com`) to the Android member client.

---

## 1. Prerequisites

- Supabase CLI installed:
  ```bash
  npm install -g supabase
  ```
- Owner account logged in:
  ```bash
  supabase login
  supabase link --project-ref shwqsljicmhflnvjqjah
  ```

---

## 2. Database Migrations (Run in Order)

Ensure all migrations from `admin-panel/supabase/migrations/` have been applied to PostgreSQL:

1. `20261105171141_202611050001_initial_schema.sql` (Tables: profiles, repair_tools, versions, devices, syncs, runs, audit)
2. `20261105171330_20261105180000_harden_rls_grants_and_indexes.sql` (RLS security hardening)
3. `20261105171827_20261105183000_deny_browser_rate_limit_access.sql` (Deny public rate limits)
4. `20261106120000_private_realtime_catalog_broadcast.sql` (Realtime publication and channel policies)
5. `20261106153000_backend_report_idempotency_and_atomic_catalog.sql` (Transactional idempotency and atomic catalogue queries)
6. `20261106160000_transactional_admin_mutations_audit.sql` (Owner mutation auditing)
7. `20261106170000_atomic_member_catalog_and_device_registration.sql` (Atomic member device registration RPC)

Apply pending migrations:
```bash
supabase db push
```

---

## 3. Cryptographic Keypair Generation & Secrets Setup

The Owner must generate an **RSA-3072** keypair for cryptographic recipe signing.

### Step A: Generate RSA-3072 Keypair
```bash
# Generate private key
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private_key.pem

# Extract public key
openssl rsa -pubout -in private_key.pem -out public_key.pem
```

### Step B: Set Supabase Edge Function Secrets
Set the private key in Supabase Secrets (never expose this to Android or public source):
```bash
# Upload private key as secret
supabase secrets set RECIPE_SIGNING_PRIVATE_KEY_PEM="$(cat private_key.pem)"
```

### Step C: Bundle Public Key in Android Client
Put the public key PEM into `.env` for the Android build:
```properties
RECIPE_SIGNING_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----
...[base64-encoded X.509 public key]...
-----END PUBLIC KEY-----"
```

---

## 4. Deploy Supabase Edge Functions

Deploy all 15 Edge Functions to the live project:
```bash
supabase functions deploy admin-assign-tool
supabase functions deploy admin-delete-draft-tool
supabase functions deploy admin-disable-tool
supabase functions deploy admin-invite-member
supabase functions deploy admin-manage-device
supabase functions deploy admin-manage-member
supabase functions deploy admin-manage-tool-state
supabase functions deploy admin-publish-tool
supabase functions deploy admin-save-validation
supabase functions deploy admin-upsert-tool
supabase functions deploy get-admin-analytics
supabase functions deploy get-my-repair-tools
supabase functions deploy get-signed-repair-tool
supabase functions deploy register-device
supabase functions deploy submit-repair-run
```

---

## 5. Owner Account Bootstrap

1. Open `https://mahi-repair-admin.onrender.com/login`.
2. Enter the Owner email address.
3. Supabase Auth will send a Magic Link or OTP.
4. In Supabase Dashboard -> Table Editor -> `profiles`:
   Ensure the owner's row has `role = 'owner'` and `status = 'active'`.

---

## 6. Inviting a Member & Enrolling an Android Device

1. From the Owner Admin Console, navigate to **Members**.
2. Click **Invite Member** and enter the member's email (e.g. `member@example.com`).
3. Member opens the Mahi Repair app (`Mahi-Repair-Installable.apk`) on their Android device.
4. Member inputs their invited email and verifies the OTP.
5. Android automatically invokes `POST /register-device` to register the device UUID.
6. The Owner will see the registered device in Admin Panel under **Registered devices**.
7. Owner publishes repair recipes; the Android app automatically syncs via Realtime!
