# Archived: Google AI Studio / Firebase migration handoff

> **SUPERSEDED — DO NOT USE AS THE CURRENT IMPLEMENTATION PLAN.**
>
> This document described a proposed Firebase Authentication + Firestore + Cloud Run migration. That cutover was not completed or authorized as the current production architecture. The live Android Member app and standalone Owner Admin website currently use one shared Supabase project: `shwqsljicmhflnvjqjah`.

The current architecture is:

- Android Member app: [Mahi-Repair](https://github.com/mahimahfud-ui/Mahi-Repair)
- Separate Owner website: [Mahi-Repair-Admin](https://github.com/mahimahfud-ui/Mahi-Repair-Admin)
- Shared backend: Supabase project `shwqsljicmhflnvjqjah`, region `ap-south-1`
- Canonical Owner website: https://mahi-repair-admin-yy7c.onrender.com

The active Kotlin runtime uses Supabase Auth, Supabase Edge Functions and private Supabase Realtime. The standalone Next.js Admin website uses Supabase SSR, server-side active-Owner authorization and the same Edge Functions. Android contains no Owner Admin UI or privileged Owner API.

Do not follow the old instructions below to:
- create or provision another Firebase/Firestore database;
- create/deploy a Cloud Run production API;
- migrate Android Auth, recipes, reports or Realtime to Firebase;
- move production roles or create replacement Owner identities;
- enable billing or paid Google services;
- deploy the old local-JSON Express staging backend.

Firebase project metadata and the historical Firebase/Cloud Run files remain for reference only. Their presence does not prove a live Firebase backend.

## Current instructions

Use these sources instead:

1. [Current Android Supabase backend contract](../BACKEND_INTEGRATION.md)
2. [Android production setup and acceptance checklist](../PRODUCTION_SETUP_CHECKLIST.md)
3. [Current build and integration boundaries](CURRENT-BUILD-AND-INTEGRATION-BOUNDARIES.md)
4. [Standalone Admin production handover](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/CURRENT_PRODUCTION_HANDOVER.md)

A change to replace Supabase with Firebase/Cloud Run must be a separate architecture proposal with an explicit Owner approval, resource/cost review, migration plan, rollback plan and full physical-device acceptance. This archived document is not that approval.
