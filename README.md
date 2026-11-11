# Mahi Repair — Android Repair Client + Separate Owner Console

Mahi Repair has two deliberately separate clients backed by the same production repair Supabase project:

- **Android member app** (`app/`): Kotlin/Jetpack Compose, invite-only Supabase Auth, registered-device authorization, signed repair recipes, native allowlisted actions, and report synchronization. The Android UI has no Owner Admin screen, route, or menu.
- **Owner Admin website** (`admin-panel/`): Next.js application with Supabase SSR sessions, active-Owner profile checks, member/device controls, recipe authoring, testing, publishing, audit history, and analytics. It is a separate web application.
- **Repair backend**: Supabase project `shwqsljicmhflnvjqjah`. Database RLS and JWT-verified Edge Functions own identity, device, publication, recipe-signature, and run-report authorization.
- **AI recipe drafting**: the Owner-only Admin assistant calls the existing MahiCloud `mahi-ai-chat` Edge Function in project `husydmiujigyonlxbuuj`. Provider keys remain in that project's Edge Function secrets; no provider credential is bundled into Android or the browser.

## Security boundaries

- Android only downloads and executes declarative recipes composed from the app's compiled action allowlist. It does not run remote shell commands, scripts, JavaScript, DEX/JAR files, or arbitrary code.
- The Admin website is protected by Supabase Auth plus a server-verified active Owner profile. Owner PIN login uses the existing Owner Auth account's password; use an 8–12 digit PIN configured in Supabase Auth.
- AI output is an untrusted draft. It cannot publish a tool or execute a repair. The Owner must validate the recipe in Test Lab and publish explicitly.
- Public Supabase publishable keys may be present in client configuration. Never commit a service-role/secret key, database password, provider key, recipe-signing private key, Owner PIN, or Android release keystore.

## Local development

Root React/API code is a legacy preview and is not the production storage path. The native Android app and the separate Admin website use the Supabase architecture above.

## Running Locally

```bash
npm install
npm run dev
```

Build for production:
```bash
npm run build
```
