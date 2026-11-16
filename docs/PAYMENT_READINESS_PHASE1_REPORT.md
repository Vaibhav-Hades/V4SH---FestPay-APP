# Payment readiness and lifecycle — Phase 1

Started: 2026-11-03. Device validation continued: 2026-11-04 (Asia/Calcutta). This report supplements the historical CORE_PAYMENT_FUNCTIONALITY_REPORT.md; it does not replace earlier test observations with new claims. No new real payment is authorized or launched by this task.

## Observed cause and limits

Before the update, the Redmi reported V4SH in enabled Accessibility services, absent from bound services, and listed in crashed services. The application process was absent; package metadata reported stopped=false. Android ApplicationExitInfo most recently recorded LOW_MEMORY at 2026-11-03 22:58:52.418 (device-reported time), with another LOW_MEMORY event at 22:32:21 and OTHER KILLS BY SYSTEM at 22:35:38.

This establishes system low-memory process death and the resulting enabled/unbound condition. It does not establish why this OEM did not subsequently rebind, nor prove a Java exception from the word “crashed” in the service list. The earlier manual-clear explanation is insufficient for this observed event. Automatic service survival/rebinding under every system condition is not claimed.

The source had no onUnbind cleanup, used a mutable connected Boolean with no instance/generation identity, and accepted session-ID-only initialization acknowledgments. The VM also persisted a payment before the ACK could fail. These were independently verified code weaknesses; a missing callback alone is not claimed to have caused the low-memory kill.

Manifest review: the service uses BIND_ACCESSIBILITY_SERVICE, the AccessibilityService intent filter and its configuration metadata. The service is exported for Android's binding and protected by the system binding permission. The private session receiver is not exported. No new permission, network dependency, foreground service or restart loop was introduced.

Android references: [AccessibilityService lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo), and [enabled-service enumeration](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager). Enabled settings are not proof of a live service connection. onInterrupt is a feedback interruption, not proof of unbinding.

## Protected real attempt

Read-only inspection: PASS. One saved record, state UNKNOWN; reservation present and matches the record; provider reference absent; provenance absent. Exact local attempt UUID and persisted UTC creation timestamp are in the private, ignored local artifact ../build/private-attempt-audit-before.json. No recipient, amount, balance or authentication text was exported. Journal and integrity-seal bytes were hashed before/after the inspection and were unchanged.

The debug-only inspection opens only an existing version-2 journal, never calls recover/create/transition, and never starts a carrier operation. It adds only a private metadata report. It is excluded from release. No reservation release or manual acknowledgment is accepted as reconciliation.

Authoritative reconciliation: BLOCKED. No authorized confirmation adapter, provider access or provider transaction reference exists in the current integration. The local UUID identifies a V4SH attempt, not a bank transaction. Creation time is only a search clue; neither it nor UNKNOWN establishes submission, settlement, failure or which bank transaction corresponds to the attempt.

Required external work: the account holder must obtain official transaction details through the bank's secure channel; an authorized bank/PSP relationship must provide a technically accessible transaction-status mechanism and the right to use it. Secure correlation needs the provider's transaction reference/UTR, source identity and authenticated response, relevant payer/account context, recipient/currency/amount and timing, and an unambiguous mapping to this local attempt. These details must be supplied only through an authorized secure channel, never PIN/OTP or chat/log capture. The provider's documented outcome semantics and correlation must meet AUTHORIZED_CONFIRMATION_CONTRACT.md. A manual statement or screenshot alone does not qualify as programmatic settlement evidence. If the original mapping cannot be established, the attempt stays UNKNOWN and protected.

## Implementation

- AccessibilityConnection owns a weak service reference and a new connection generation. Settings enablement is read separately. Actual connection requires the current lifecycle owner and a live Android service connection. Old-instance teardown cannot clear a newer owner; duplicate connection callbacks preserve the existing lease. An observed dead binding latches disconnected and invalidates its command/ACK, so a later successful getter cannot resurrect a stale session without a fresh lifecycle connection.
- onUnbind and onDestroy clear only their own connection/session and unregister their own receiver. onInterrupt disarms the session without pretending that a feedback interruption necessarily unbound the service. A new connection never arms, replays, launches or retries an old operation.
- Preparation claims an exclusive session and initializes it inertly. The ACK must match the current session, connection generation and command; it is rejected after timeout, release or reconnection. Duplicate initialization is idempotent and does not reset progress.
- Both QR and manual Review use the same VM entry point. It checks consent, positive normalized amount, VPA, current immutable Review, Phone permission, observable cellular prerequisites, storage and the existing reservation. Missing ACK fails before financial creation. Readiness is rechecked after the asynchronous ACK.
- A planned immutable UUID is persisted as INITIATED with its reservation before arming. The real engine rechecks the matching durable PROCESSING record/reservation and exact command before arming and requesting ACTION_CALL. There is no suspension between final synchronous checks/arming and launch request. Only this fresh user-initiated path can arm a payment.
- SQLite persistence and Android's external call cannot form one atomic transaction. A failure/crash after durable intent creation remains conservatively UNKNOWN/reserved, even when no carrier window was observed. Such an ambiguous failure is not erased to make history look empty. Ordinary failed preflight or missing ACK creates no payment record.
- Balance retains separate payload/ownership and explicitly arms its own acknowledged session. No payment history is created by balance. Consent withdrawal invalidates the lease/ACK and stops observation; no financial result is inferred.
- Readiness refreshes settings/consent on resume and observes Accessibility setting changes and Compose connection state. An enabled-but-disconnected service has the manual action: Android Accessibility → V4SH OFF → ON → return to V4SH. No automatic restart/retry is attempted.
- Diagnostics contain only enabled/connected/instance/initialized/ACK/launch-allowed booleans. They contain no session IDs, recipient, amount, PIN, OTP or raw carrier content. LAUNCH_ALLOWED can be true only in the synchronous real-engine launch path after the durable match; an idle readiness screen is not launch authorization.

Files: AccessibilityConnection.kt, AccessibilityConsent.kt, FestPayAccessibilityService.kt, UssdSessionTransport.kt, PaymentAttemptJournal.kt, PaymentViewModel.kt, PaymentSetupScreen.kt, ScreenPlaceholders.kt, debug/AndroidManifest.xml, debug/PaymentReadOnlyAuditInstrumentation.kt; AccessibilityConnectionTest.kt (new), PaymentReadinessLifecycleTest.kt (new), ReadinessConsentTest.kt and UssdSessionTransportTest.kt (updated); CORE_PAYMENT_FUNCTIONALITY_REPORT.md and this report. Existing working encryption, central financial transition policy, UNKNOWN guard, no-INTERNET policy and manual PIN boundary remain in place.

## Verification

All 36 Android tests passed on the Redmi on 2026-11-04 after the user disabled Accessibility and confirmed the phone was unlocked. The requested physical service lifecycle checks now PASS, with direct Android metadata and user UI confirmation. Phase 1 service/readiness validation is complete for this Redmi and tested build. Financial reconciliation remains BLOCKED; this is not production approval or a live-payment completion claim.

| Check | Result |
|---|---|
| Pre-change local build/test baseline | PASS; original task baseline retained |
| Real attempt read-only inspection | PASS; UNKNOWN, protected, no provider reference; bytes unchanged |
| Complete JVM suite | PASS — 88 tests, 0 failures/errors, including 6 new connection lifecycle cases |
| Complete final Android suite | PASS — 36/36, 54.268 seconds, all 31 prior cases retained plus 5 new cases; build/readiness-android-full.log |
| Debug/test/release builds and release lint | PASS — final debug/test/signed release builds; release lint 0 errors, 40 warnings, 2 hints. Final combined run: build/readiness-complete-check.log; sequential consistency check: build/readiness-final-consistency.log |
| Physical enabled/bound and manual recovery | PASS — user OFF/ON and settings-button recovery verified; final UI and actual Android binding agree; fresh idle INIT/ACK/LAUNCH all false |
| Background/resume | PASS — service remained actually bound after Home → resume; no session initialized or armed |
| Force-stop/reopen, no relaunch | PASS — stopped process absent, reopen returned to V4SH, no automatic carrier/payment launch; UI correctly showed disabled/disconnected/instance absent and payment blocked, then recovered through its settings button |
| Real reservation after installation/tests/lifecycle | PASS — exact private pre/post-test metadata matches the original; journal and seal hashes remain unchanged through final service restoration; UNKNOWN reservation protected |
| Authorized reconciliation / live payment completion | BLOCKED; external authorization and correlation absent |

No commit or push. No production-ready or end-to-end payment-success claim.

Final debug and test APKs installed with data-preserving replacement. No uninstall or app-data reset. The read-only audit passed before and after the complete suite; exported ID/timestamp/state/reservation/reference-presence metadata equals the earlier private artifact. Final physical lifecycle acceptance passed as recorded below. No carrier operation was launched.

Final signed release SHA-256: a2af0bd80683e1d7d4d4aa7391c4df9f2bae35f1fc01a059abcbc0c92151a67e. The release manifest excludes the read-only audit instrumentation; debug/test fixtures are not a production confirmation source. The release was built, not installed over the differently signed debug application.

## Device continuation — 2026-11-04

- Before this installation, Android reported V4SH actually bound, enabled and not crashed. The earlier enabled/unbound observation was historical; the time/cause of this intervening recovery was not established, and it is not credited as proof of automatic recovery by the final build.
- User confirmed Accessibility OFF and phone unlocked. Direct system metadata verified V4SH absent from both bound/enabled lists. Final debug and test APKs then installed successfully without clearing data.
- The full 36-test Android run completed PASS, including all 5 new synthetic preflight/consent/planned-ID cases. No tests were removed or suppressed.
- Real attempt audit PASS before/after tests. Local ID, creation time, UNKNOWN state, reservation/reference-presence metadata exactly matches the original private report. New private artifacts: build/private-attempt-audit-pre-tests.json and build/private-attempt-audit-post-tests.json.
- At this intermediate checkpoint restoration was pending. It subsequently passed, including readiness UI confirmation, background/resume and force-stop/reopen, as recorded in Final physical acceptance below. Synthetic test-host CONNECTED logs were excluded from real-binding evidence; the final physical checks used fresh real-service diagnostics plus Android bound-service metadata.
- Journal/seal hashes were captured privately before physical lifecycle checks (build/readiness-storage-before.sha256); final comparison passed after service restoration.

## Physical lifecycle observations — 2026-11-04

The user restored Accessibility and confirmed the final Readiness screen showed enabled, connected and instance available. Android independently listed V4SH bound, enabled and not crashed; real-service PID 9025 emitted ENABLED=true CONNECTED=true INSTANCE=true INITIALIZED=false ACK=false LAUNCH_ALLOWED=false. These are fresh real-service observations, not the synthetic test-host logs.

Home/background followed by MainActivity resume retained that real binding and the same safe idle diagnostics. A requested force-stop removed the process; reopening brought MainActivity to foreground. On this particular Redmi/Android build, force-stop also removed V4SH from the Accessibility enabled list, so automatic connection restoration was not assumed. No current-process payment-engine launch diagnostics appeared. Neither the protected journal nor its integrity seal changed (exact SHA-256 comparison with the pre-lifecycle baseline). No payment or balance check was launched.

The user subsequently confirmed the post-force-stop disconnected readiness UI and settings-button recovery. Final physical acceptance below records restored binding and unchanged storage. No additional app restart or instrumentation run was used after restoration; final verification used read-only hashes without stopping the service.

## Final physical acceptance — 2026-11-04

PASS. The user confirmed the post-force-stop Readiness UI showed disabled/disconnected, instance absent, and the earlier unresolved attempt still blocked payment. Its Open Accessibility settings button opened Android settings; the user enabled V4SH and returned to an enabled/connected/instance-available screen. Fresh same-process diagnostics (PID 12618) independently recorded the transition from ENABLED=false CONNECTED=false INSTANCE=false to ENABLED=true CONNECTED=true INSTANCE=true. INITIALIZED=false ACK=false LAUNCH_ALLOWED=false in both idle states. Android then independently reported V4SH actually bound, enabled and not crashed. This demonstrates honest cold-process disconnected state and successful explicit recovery without claiming automatic reconnection after force-stop.

The final journal and integrity-seal SHA-256 values exactly match the pre-lifecycle baseline (build/readiness-storage-final.sha256 versus build/readiness-storage-before.sha256). Combined with the pre/post-test read-only decrypted metadata comparison, the original exact local UUID, UTC timestamp, UNKNOWN state and reservation remain intact; no bank/provider reference or provenance appeared. No final instrumentation restart was used after restoration; the service was left connected. No real payment or balance operation was initiated, no authentication content captured, no reservation released, and no commit/push performed.

Completion: the requested service/readiness implementation and local/Redmi validation PASS (88 JVM, 36 Android, debug/test/release builds, release lint, manual recovery, background/resume, force-stop/reopen, no automatic payment and unchanged reservation). Coverage is limited to the tested build/device and these observed transitions; future OEM low-memory behavior is not guaranteed. The historical LOW_MEMORY exit is established; the exact OEM reason for failing to rebind at that earlier time remains NOT ESTABLISHED. Authorized reconciliation remains BLOCKED, original bank-transaction correlation remains UNAVAILABLE, and SUCCESS/FAILED authorization stays BLOCKED. No production approval or end-to-end financial completion claim.
