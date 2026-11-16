# Production payment journey audit and implementation

Date: 2026-11-05. Scope: local source audit and isolated tests only; no financial operation.

## Journey audit

Paths below are relative to `D:/FestPay/app/src/main/java/com/festpay/app/`.

| Step | Source/functions | Finding |
|---|---|---|
| QR/manual entry and Review | ui/screens/ScreenPlaceholders.kt: SharedPaymentState, ReviewPaymentScreen; data/PaymentRecipient.kt: review/isCurrent | Shared immutable recipient/amount/note snapshot; QR name explicitly unverified. Pay revalidates snapshot. Implemented; prior physical preparation checks passed. |
| Pay preflight | ui/viewmodel/PaymentViewModel.kt: startPayment | Consent, actual binding, phone/cellular readiness, storage, reservation and current Review gate initiation. Existing UNKNOWN blocks before handshake. |
| Prepare/arm ordering | PaymentViewModel.startPayment; data/UssdSessionTransport.kt: prepare/arm; data/AccessibilityConnection.kt: arm | FOUND AND FIXED: coordinator prematurely armed before durable creation; engine then attempted a second arm, rejected by the single-use lease. Preparation is now inert; only the engine arms after durable matching PROCESSING/reservation checks. |
| Durable state | data/PaymentAttemptJournal.kt: create/transition/recover | INITIATED and reservation created atomically before external interaction; PROCESSING then UNKNOWN. Interrupted INITIATED/PROCESSING recover to UNKNOWN. Existing reservation and noncancelled record prevent duplicate creation. No journal change. |
| Engine/initiation | ui/viewmodel/PaymentViewModel.kt: PaymentEngine, RealUssdPaymentEngine.executePayment | Same engine validates immutable command and reserved durable record, arms once and requests ACTION_CALL tel:*99#. Exception does not authorize failure or retry. Actual owned payment launch remains physically unproven. |
| Connection lifecycle | data/AccessibilityConnection.kt; FestPayAccessibilityService.onServiceConnected/onUnbind/onDestroy | Instance identity, generation, session command, ACK and one-shot arm; disconnect/reconnect does not resume old automation. |
| Event admission | FestPayAccessibilityService.onAccessibilityEvent; data/UssdSourcePolicy.kt | Consent/owned armed session, approved system package, event/root agreement and launch-time freshness; reducer rejects duplicate/older event times. Password/input values excluded. |
| Menu navigation | data/CarrierPaymentFlow.kt: observe/rootOption/methodOption | Digits derived from unique supported observed labels. No guessed payment menu numbers. Unknown prompts stop assistance; remarks/purpose variants remain unsupported. |
| VPA and amount | CarrierPaymentFlow.observe/inputResult; service.handlePaymentObservation | Frozen command values offered once for supported prompts. OS action success is not carrier acceptance. FOUND AND FIXED: pre-write and pre-Send fresh prompt checks now apply to every payment input, not only amount; active window/package and ownership are rechecked. |
| Exact review | data/UssdNavigationPolicy.kt: isMatchingReview; CarrierPaymentFlow.parseReview | Expected complete VPA token and one exact INR amount required. Mismatch interrupts. Carrier beneficiary text remains unverified. Real Airtel review-format compatibility unproven. |
| Native confirmation | CarrierPaymentFlow REVIEW; CarrierPaymentJourneyScreen | No automated Confirm and no app confirmation behind modal carrier dialog. User confirms natively. Subsequent supported PIN instruction is an operational observation, not proof of a click or completed authentication. |
| PIN boundary | service.hasPasswordField/collectAllTexts; UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt | Input/password subtrees excluded, generic password field insufficient; no PIN read/write/click automation. Narrow supported static instruction recognition may reject unqualified carrier variants. |
| Result and closure | CarrierPaymentFlow PIN/RESULT; data/CarrierDialogLifecycle.kt; service.scheduleReturnAfterDialogRemoval | Wording never settles payment. Window removal plus complete non-phone window evidence and V4SH active root required; rechecked before one-shot return. API <28/incomplete metadata cannot qualify. |
| MainActivity return | service.scheduleReturnAfterDialogRemoval; CarrierPaymentJourneyScreen LaunchedEffect | Conservative completion of Android/user return only: V4SH must already be usable. Does NOT force an automatic return from Phone/Recents. Missing closure evidence requires manual return and Stop assistance. Lost process/navigation state falls back to saved history/readiness. This is a remaining UX limitation, not claimed complete auto-return. |
| Final financial outcome | data/PaymentIntegrityPolicy.kt; PaymentViewModel.checkStatus | SUCCESS and FAILED transitions disabled without authorized evidence. checkStatus has no bank source. UNKNOWN is retained; no automatic retry. No provider verifier/reconciler exists. |

## Minimal changes

- PaymentViewModel.kt: remove premature arm from inert preparation; retain the engine's durable-record-checked arm.
- CarrierPaymentFlow.kt: pure action-specific prompt revalidation predicate.
- FestPayAccessibilityService.kt: refresh/revalidate current owned window and supported prompt before writing and before Send; confirmation remains forbidden.
- New CarrierInputRevalidationTest.kt: changed menu digits, swapped recipient/amount prompts, noneditable windows, review and PIN rejection.
- This report. No permissions, backend, new engine, history changes or journal mutations introduced.

Existing tests cover changed Review after ACK (host must never arm), durable planned IDs, wrong ownership/generation, stale events, exact frozen values, native confirmation, password exclusion, conservative closure, UNKNOWN navigation, duplicate prevention, crash recovery, and rejection of both SUCCESS and FAILED without authority. Fixtures do not establish real carrier input acceptance.

## Reconciliation blocker and exact next action

The existing reservation cannot be cleared in this task. There is no production authorized confirmation adapter, provider reference/correlation binding or evidence-consuming reconciliation path. A new status API or Boolean flag alone would not resolve the old attempt.

Obtain an authorized bank/PSP reconciliation procedure covering this existing *99# transaction. It must establish the relevant account/principal, unique transaction correlation, recipient, exact INR amount, authenticated provider reference and provenance, final status semantics, occurrence time, and replay/conflict handling. A timeout, not-found result, screenshot, user acknowledgment, balance delta or carrier wording is insufficient. The provider must support retrospective correlation of this old attempt; do not manufacture a binding to a new order.

Once that legitimate mechanism is established, a separately reviewed evidence-validation/reconciliation implementation is required to consume it atomically under an approved finality policy. Current code intentionally cannot release UNKNOWN even if someone supplies an informal status. Until both evidence and its authorized consumption path exist, no new live payment is permitted. See AUTHORIZED_CONFIRMATION_CONTRACT.md for the existing contract requirements. No external integration was added here.

## Physical-device checklist

| Check | Implemented | Automated evidence | Physical evidence / next controlled check |
|---|---|---|---|
| QR/manual preparation and frozen Review | Yes | Parser/preparation/UI fixtures | Previously user-confirmed; latest changes do not establish payment completion |
| Accessibility connection and recovery | Yes | Connection/transport fixtures | Final restored binding confirmed by user and Android; no carrier operation |
| Native initial menu observation | Yes | Source/reducer fixtures | Prior Redmi/Airtel read-only observation proved package/window and advertised actions only |
| Actual payment launch through V4SH | Yes, ordering fixed | Preflight/journal/connection tests | NOT PROVEN; blocked by existing UNKNOWN |
| Carrier accepts VPA/amount and progresses | Supported formats only | Frozen-input/prompt tests | NOT PROVEN; action advertisement and local widget tests are insufficient |
| Exact carrier review and native confirmation | Yes for supported labels | Matching/mismatch/native boundary tests | NOT PROVEN on a real owned payment |
| Manual PIN handoff | Yes for supported instructions | Secret exclusion and stage tests | No actual payment authentication performed |
| Dialog closure/return | Conservative fallback | Window lifecycle and navigation fixtures | Automatic owned-payment return NOT PROVEN; modal blocking previously observed |
| Actual financial transaction | No claim | Financial policy tests reject unsupported finality | NOT PROVEN; authorized reconciliation must precede any controlled live test |

After reconciliation legitimately permits a live attempt, obtain explicit user authorization for one controlled payment, verify recipient/amount on native carrier Review, leave PIN entry entirely manual, and record only non-sensitive operational evidence. Validate server acceptance and authoritative finality separately. This task does not authorize that test.

## Validation results

- JVM: 119 tests, zero failures/errors, including two new input-revalidation cases. XML evidence: app/build/test-results/testDebugUnitTest/.
- Complete Android suite: 47/47 PASS in 75.791 seconds, build/production-journey-android.log. Includes the existing changedReviewAfterAcknowledgmentCreatesNoAttempt test: its host throws if preflight arms, so it guards the repaired ordering before durable creation. No test deletes/relaxes the real reservation.
- Debug, test APK and signed/minified release builds: PASS. Debug lint and release vital lint: PASS. build/production-journey-build.log, 2m 58s. Gradle deprecation warnings remain; no unrelated build refactor.
- git diff --check: PASS (existing line-ending warnings only).
- Updated debug/test APKs installed with existing data retained. No real payment or balance operation, commit or push.
- Protected real journal and seal SHA-256 fingerprints match exactly before/after testing (build/production-journey-before.sha256 and build/production-journey-after.sha256). The existing UNKNOWN record/reservation were not modified. User confirmed enabled/connected/instance available; Android independently lists V4SH bound with no crashed/binding entry after restoration. No further instrumentation or app restart followed restoration.
