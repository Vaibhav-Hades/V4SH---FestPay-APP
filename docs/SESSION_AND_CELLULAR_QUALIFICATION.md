# Session and cellular qualification

## Implemented controls

- Each user-started balance or payment operation has a unique ephemeral session UUID. A payment separately carries its immutable durable attempt UUID and frozen validated VPA/amount. A balance operation has no payment fields.
- Main-thread acquisition prevents concurrent owners. An explicit app-scoped broadcast must be acknowledged by the service with the same UUID before dialing. A deadline only means initialization was not acknowledged; it proves no authentication, submission or settlement.
- Busy operations cannot replace an owner. Old/wrong acknowledgments cannot claim a new session. Release is conditional on identity; service reconnect does not resume an old dialog. Durable unresolved-payment protection is independent of this ephemeral lease.
- Service observation requires active ownership, approved system phone-package identity, matching root/event packages and windows, and a non-stale event timestamp before reading static labels. Editable/password values are excluded; password fields stop automation before reading labels. Unrecognized sources/layouts halt rather than broadening to arbitrary apps.
- Navigation requires known root options and ordered recipient/amount steps. Payment confirmation requires matching reviewed recipient and a single matching currency amount plus explicit confirmation controls. Remarks/ambiguous prompts are not guessed. Carrier wording remains an observation only and cannot authorize SUCCESS or FAILED.
- Consent is separately required before launching and processing a session. It is optional and withdrawable. Closing a review cannot cancel a previous UNKNOWN record or release its reservation.

## Readiness observations

Public Android metadata reports telephony hardware, airplane mode, coarse SIM readiness and an operator label where available. Known no-hardware, airplane-on, absent-SIM and locked-SIM observations prevent initiation before journal creation. Missing metadata remains cannot verify. No subscriber numbers, IMSIs, SIM identifiers, bank credentials, location or SMS access is required (READ_PHONE_STATE is requested only by the optional SIM chooser).

The default Android SIM state/operator observation is not a full multi-SIM inventory. V4SH does not select a bank SIM or prove calling-SIM routing, bank registration, radio coverage or carrier support. Android/carrier controls routing. A READY SIM or operator label must never be presented as payment readiness or financial proof.

## Evidence and qualification limits

JVM tests cover root/amount/review matching, source/stale-event rejection, owned payload validation and unavailable readiness conditions. Device tests use a private fake broadcast action to cover acknowledged reset, busy cross-operation ownership, wrong/late acknowledgment rejection and sequential QR-state clearing. They never invoke the real carrier or a payment. Public-readiness and consent tests additionally verify permission-minimal reads and rejection before journal access.

The user's Redmi/Airtel offline balance and preparation checkpoints passed on prior builds. The final target-36 build passed a fresh offline balance check and retained-profile/history confirmation; the user also verified repeat QR, manual ID/amount Review, Receive/history/details and Help/chooser navigation. Full automated suite: 28 device tests passed. Other banks/carriers/devices/languages, real dual-SIM switching, physical SIM removal/lock and real coverage loss have NOT BEEN TESTED. These limits remain release qualification work, not simulated PASS results.
