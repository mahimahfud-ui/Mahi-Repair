# Archived: Google AI Studio / Firebase remaining-work list

> **SUPERSEDED — DO NOT EXECUTE THIS PLAN.** The Firebase/Cloud Run tasks below were a proposed migration, not the current production implementation plan. The current Android and separate Owner Admin applications use the shared Supabase project `shwqsljicmhflnvjqjah`.

Do not provision Firebase/Firestore, Cloud Run, Secret Manager resources, a second backend or paid services based on this document. Do not migrate the Android Supabase Auth, Edge Function, recipe, report or Realtime clients as routine completion work.

The current completion path is:

1. Verify canonical Render Admin deployment and Supabase Auth URL settings.
2. Test Owner PIN login and Member denial.
3. Verify Auth Send Email Hook and real invited-Member OTP delivery.
4. Securely verify the production RSA private/public recipe-signing keypair.
5. Complete physical Android device registration and the Member → signed Repair Tool → safe execution → Admin report workflow.
6. Complete production release signing using the existing Android upload key.
7. Port and stage the unmerged Live App Update Engine separately.
8. Implement optional Device Intelligence/Remote Support only after a durable authenticated backend is ready.

Authoritative current references:

- [Android Supabase backend contract](../BACKEND_INTEGRATION.md)
- [Android production checklist](../PRODUCTION_SETUP_CHECKLIST.md)
- [Current build boundaries](CURRENT-BUILD-AND-INTEGRATION-BOUNDARIES.md)
- [Standalone Admin deployment and production handover](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/CURRENT_PRODUCTION_HANDOVER.md)

Any future Firebase/Cloud Run migration requires a separate explicit Owner approval and a complete resource, billing, data migration, rollback, security and physical-device acceptance plan.
