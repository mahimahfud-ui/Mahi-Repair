# Archived: Google Backend Cutover Checklist

> **NO-GO — THIS IS NOT AN APPROVED PRODUCTION CUTOVER.**
>
> This checklist was drafted for a proposed Firebase/Cloud Run migration. Current production Android and standalone Admin clients use the shared Supabase project `shwqsljicmhflnvjqjah`. No Firebase/Cloud Run cutover should be inferred from this archived checklist.

Do not use this file to provision Google infrastructure, enable billing, create a new database, migrate identities, replace Android API clients, disable Supabase or change production routing.

Before any future backend replacement, require a separate Owner-approved architecture proposal covering:
- exact existing and target resources, project/database IDs and regions;
- itemized recurring and usage-based costs;
- verified Owner/Member identity mapping without role promotion;
- complete Auth, device, recipe-signing, repair-run, audit and Realtime migration;
- default-deny authorization and automated security testing;
- physical Android and real Admin acceptance;
- deployment, rollback and data preservation;
- explicit approval before paid resources or production cutover.

For current work, use:

- [Android Supabase backend contract](../BACKEND_INTEGRATION.md)
- [Android production setup checklist](../PRODUCTION_SETUP_CHECKLIST.md)
- [Current build boundaries](CURRENT-BUILD-AND-INTEGRATION-BOUNDARIES.md)
- [Standalone Admin current handover](https://github.com/mahimahfud-ui/Mahi-Repair-Admin/blob/main/docs/CURRENT_PRODUCTION_HANDOVER.md)
