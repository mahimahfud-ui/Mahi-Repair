# Remote support readiness and backend gaps

## Verified current implementation

The current repository branch has a secure recipe-delivery and repair-telemetry workflow:

- Android registers a server-issued device ID and reports only a device label, app version, Android version and API level.
- Android receives private device-scoped Realtime Broadcast events. Those events carry only an opaque event ID and trigger a fresh authorized catalogue fetch.
- The owner console can invite a member, suspend/revoke a member, revoke/restore a device, assign repair tools, publish/disable recipes, and review repair-run telemetry and administrative audit events.
- Android executes a fixed native action allowlist. A recipe is data, not remotely executable code.

These features are not a remote screen-sharing system.

## Not present in the current backend or Android client

The repository does not currently provide:

- A support-request or support-session table/API.
- Owner/member consent and approval state for a support session.
- WebRTC offer/answer/ICE signaling endpoints or a signaling topic contract.
- STUN/TURN configuration, TURN credentials, connection-quality telemetry or reconnection state.
- Android MediaProjection capture, foreground screen-capture service or user-visible capture consent flow.
- A browser WebRTC peer, remote video renderer, disconnect signaling, session timer or remote support history.

The existing Supabase Realtime subscription is device-scoped catalogue invalidation only. It must not be reused as an unrestricted signaling or screen-stream channel. There is no video stream to render, and the console intentionally does not simulate one.

## Required work before live laptop-to-Android support can be enabled

1. **Consent and authorization contract**
   - Add a server-created support request tied to one active member, one registered non-revoked device and one authenticated Owner.
   - Require the Android user to explicitly accept a named Owner/support request on-device before capture starts.
   - Persist requested, accepted, rejected, expired, connected and ended timestamps plus the actor that caused each transition.
   - Re-check active member, device ownership, device revocation and Owner role at every signaling/renewal operation.

2. **Signaling and media**
   - Define short-lived, private session-scoped signaling for SDP offers/answers and ICE candidates.
   - Use authenticated private channels or narrowly scoped authenticated Edge Functions; never expose service-role credentials or broad topic access to the browser.
   - Configure STUN/TURN with short-lived credentials and measure relay/connection quality without retaining media.
   - Implement WebRTC capture and foreground-service lifecycle in Android with Android-version-specific MediaProjection consent.
   - Implement the browser peer, actual video element, session timer and disconnect cleanup.

3. **Audit and privacy**
   - Audit support request, consent, connection, disconnect, rejected/expired request and owner/device revocation.
   - Never record screen frames or personal content in the audit trail.
   - Enforce retention limits for SDP/ICE/session metadata and ensure ended sessions cannot be rejoined with stale credentials.

4. **Tests and acceptance**
   - Test member consent, denial, timeout, revoked device, suspended member, expired Owner session, reconnect, network switching and multiple devices.
   - Verify that ending a session invalidates further signaling and closes both peers.
   - Demonstrate a real Android screen stream on a physical device before exposing a “Start remote support” action in the Owner console.

Until these contracts and Android capture are implemented and tested, the production-safe behavior is to show remote support as unavailable and keep all remote screen/session controls out of the UI.
