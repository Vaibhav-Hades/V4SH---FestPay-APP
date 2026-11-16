# V4SH core payment journey

Date: 2026-11-04 (Asia/Calcutta). Implementation extends the existing engine, VM, encrypted journal, AccessibilityService and session ownership. No new payment engine or reservation bypass. No commit or push.

## Overall result

The current focused update passes 107 JVM and 42 Android tests, debug/test/signed release builds and release lint. Fresh QR/manual preparation checks passed as recorded below. The complete live payment journey is **NOT COMPLETE**. The protected real UNKNOWN attempt prevents a new carrier payment, and no authorized bank/PSP confirmation/correlation adapter is configured. Synthetic carrier labels and UI fixtures are not physical carrier evidence.

## Milestones

| Milestone | Status | Implementation and evidence |
|---|---|---|
| 1. QR → validated details → Review | PASS | Existing bundled offline scanner retained. Parser explicitly returns canonical INR, amount and local note; rejects duplicate fields, unsupported currency, invalid precision, malformed UTF-8 replacement characters, control/directional formatting and invalid Unicode. QR pn never becomes beneficiary identity. Two different supported QRs reached correct Review without stale details, confirmed by the user on the final build. |
| 2. Manual UPI ID → Review | PASS | Existing shared preparation and local syntax validation retained; reviewed amount/currency/note are frozen, beneficiary verification stays UNAVAILABLE. User confirmed invalid ID blocked and valid ID/amount retained on Review; UNAVAILABLE status and unresolved-attempt Pay block correct. |
| 3. Review → readiness/ACK/reservation → carrier initiation/response | BLOCKED | Existing actual-binding/epoch/ACK/preflight and durable INITIATED-before-interaction protections retained. Response-driven payment handling added inside the existing AccessibilityService. Real launch is prohibited by the existing UNKNOWN reservation. |
| 4. V4SH amount entry → populate → acceptance observation | PARTIAL | The existing service automatically supplies the immutable pre-Pay reviewed amount on a fresh supported editable amount prompt. No second amount entry or amount-authorization tap. Set-text/Send is not acceptance. A later matching review must show exact VPA and INR amount. Actual carrier/foreground-window retention unverified because milestone 3 is blocked. |
| 5. Carrier details → V4SH review → explicit confirmation | PARTIAL | Extracts only explicit supported carrier review labels. Carrier-displayed name is unverified observational provenance, never bank-verified identity. A separate user tap authorizes that review revision; changed details invalidate it. Actual carrier behavior unverified. |
| 6. Manual carrier PIN → final response | BLOCKED | Password flags and sensitive-label checks precede action logic; no PIN widget in V4SH, no input-value reads, no authentication automation. Only an observed authentication screen following explicit confirmation advances the operational stage. Result wording is an observation, never settlement authority. No live PIN/payment permitted. |
| 7. Explicit-attempt history and truthful outcome | PARTIAL | Existing UNKNOWN reservation/history and terminal-evidence rejection retained. Typed, encrypted, deduplicated carrier observation events added without changing financial state, reference or reservation. Authorized SUCCESS/FAILED and original transaction correlation remain BLOCKED. |

PASS requires both applicable automated checks and required physical evidence. PARTIAL above does not mean a live carrier transfer passed. The higher-stage implementation is exercised only through the explicitly authorized isolated/non-settling fixtures while the real launch prerequisite remains blocked.

## Features implemented

- Canonical supported QR currency retained in parsed output and shown on Review. QR name remains requester-controlled and excluded from verified identity. Invalid QR clears the previous recipient/amount/note through the existing shared preparation path.
- One payment journey route uses the existing selected attempt and existing owned service session; mutable QR/manual drafts cannot change the initiated request.
- A pure operational reducer replaces the old automatic payment confirmation branch inside the existing service. It cannot dial, write financial state, acquire a session or launch another payment. The existing service/connection registry remains the sole session owner.
- Root/method/confirmation choices come from uniquely observed supported English menu instructions, rather than universal digit assumptions. Ambiguous choices and unsupported prompts stop assistance; no guessed remarks, fallback pay command or auto retry.
- VPA submission waits for an observed amount prompt. Amount is only observed accepted when an exact matching carrier review appears. No time delay proves acceptance, authentication or completion.
- The pre-Pay amount remains frozen and is populated automatically at the observed carrier prompt. Separate explicit carrier-review confirmation binds to the current revision and active command/attempt. Duplicate/stale events do not replay input; disconnect, ownership loss, storage error or unsupported/mismatching review blocks further actions.
- Input submission refreshes the current root and rechecks password/sensitive flags before Send. No editable/password node's value is read. Names/amounts are not logged.
- Financial UNKNOWN is separate from operational progress: the engine can honestly return UNKNOWN while the same already-initiated interaction is being observed. The new journey screen does not unlock a new payment or call SUCCESS/FAILED.
- Operational event IDs derive from the existing attempt/session/revision; only typed stage/issue/amount-match/result-wording codes are stored, encrypted in the existing event table. No raw carrier labels, name, PIN, OTP or authentication text is serialized into these events. Same-state observations do not authorize financial transitions or release a reservation.
- Stop assistance releases only the matching current operational session, preserves UNKNOWN/history/reservation and never cancels settlement or retries. Cold restart/reconnect cannot restore an input authorization.

## Amount and Android interaction constraints

The amount must already be fixed at the first Review to preserve the existing durable immutable request before external interaction. The carrier stage now automatically uses that same reviewed amount; it cannot change an initiated attempt's amount or ask for another amount entry. Changing amount requires leaving an uninitiated draft, not editing a reserved financial attempt.

The app does not use private APIs, root, reflection, SMS/bank-app scraping, a network adapter or a second engine. It does not forcibly bring an app screen over a carrier authentication dialog. On devices where Android permits returning to V4SH and retaining the original USSD dialog, the user can confirm the observed review in the existing V4SH journey, then return to that same dialog for an owned, newly observed matching prompt. This retention/focus behavior is NOT VERIFIED for a live Redmi payment. If Android closes or hides the session, assistance stops/UNKNOWN remains; no new *99# request is issued. No seamless cross-window behavior is claimed from a Compose test.

Supported parsing is intentionally conservative: explicit numbered banking menus, UPI ID prompt, positive INR amount prompt, exact recipient/amount review, optional explicitly labelled Beneficiary/Payee name, explicit confirmation instruction/control. Unrecognized bank/carrier language, optional remarks/purpose, fees/multiple amounts, masked/missing VPA or unverifiable values are not guessed. Real carrier support must be established through an authorized controlled session after the reservation is legitimately reconciled.

## Files changed

| File | Change |
|---|---|
| app/src/main/java/com/festpay/app/data/UpiQrParser.kt | Canonical currency and invalid text/encoding validation |
| app/src/main/java/com/festpay/app/data/CarrierPaymentFlow.kt | New operational-only observation/input reducer |
| app/src/main/java/com/festpay/app/data/UssdNavigationPolicy.kt | Confirmation choice from explicit unique observed instructions |
| app/src/main/java/com/festpay/app/data/AccessibilityConnection.kt | Amount/review authorization through the existing owned host interface |
| app/src/main/java/com/festpay/app/FestPayAccessibilityService.kt | New reducer integration, removal of auto-confirm branch, reactive activity, static-only labels and refreshed sensitive checks |
| app/src/main/java/com/festpay/app/data/PaymentAttemptJournal.kt | Encrypted idempotent operational observations; no financial-state/guard bypass |
| app/src/main/java/com/festpay/app/ui/viewmodel/PaymentViewModel.kt | Current-attempt carrier actions and safe stop; existing engine preserved |
| app/src/main/java/com/festpay/app/ui/screens/CarrierPaymentJourneyScreen.kt | Amount status/review/PIN-guidance/wording journey UI; second amount editor removed in the focused update |
| app/src/main/java/com/festpay/app/ui/screens/ScreenPlaceholders.kt | Currency Review label and owned active-journey routing |
| app/src/main/java/com/festpay/app/ui/navigation/MainNavHost.kt | Journey route using the same VM |
| app/src/test/java/com/festpay/app/CarrierPaymentFlowTest.kt | 15 synthetic operational protocol cases |
| app/src/test/java/com/festpay/app/QrJourneyInputTest.kt | 3 QR/currency/identity/stale-request cases |
| app/src/test/java/com/festpay/app/UssdNavigationPolicyTest.kt | Added malformed/embedded/multiple-address review rejection case |
| app/src/androidTest/java/com/festpay/app/CarrierJourneyUiTest.kt | 4 non-settling UI cases |
| app/src/androidTest/java/com/festpay/app/CarrierObservationJournalTest.kt | 2 isolated encrypted event/recovery/authority cases |
| docs/CORE_PAYMENT_JOURNEY_REPORT.md | This report |

## Tests and builds

Baseline retained: 88 JVM and 36 Android tests. No baseline test removed or suppressed.

New unit cases cover observed menu numbering; amount authorization/value/precision; submission versus acceptance; matching review plus explicit tap; changed review/stale revision; wrong amount/VPA; duplicate/stale events; ambiguous/unsupported confirmation; interruption/disconnection/no retry; unexpected authentication; manual PIN/final wording without authority; transient/out-of-order responses. QR cases cover canonical fields, untrusted pn, malformed/directional payloads and stale draft clearing.

New Android cases cover no second amount editor or premature confirmation, unverified carrier name and explicit tap, disconnected submission blocking, PIN guidance with zero editable fields, encrypted observation deduplication without settlement/reservation release, wrong-attempt rejection and recovery. Test databases are prefixed test_; no real journal reset or real payment.

| Check | Result |
|---|---|
| Complete JVM suite | PASS — current focused update 107 tests, 0 failures/errors; two automatic-amount safety cases added to the preceding 105-case suite |
| Debug/test APK builds | PASS — build/core-journey-verified-debug.log and build/core-journey-test-apk.log; final debug installed with data preserved |
| Complete Android suite | PASS — current update 42/42 in 52.255s, build/payment-auto-amount-android.log. Earlier iteration failures/results remain documented below; none is counted as a current PASS |
| Signed release build and release lint | PASS — final build/core-journey-final-all.log; lint 0 errors, 40 warnings, 2 hints |
| Protected real attempt after install/tests | PASS — private decrypted metadata exactly matches original, and journal/seal bytes match the pre-install SHA-256 baseline. Final post-preparation hashes also match the pre-install baseline |

## Physical evidence

Target: Redmi Note 13 Pro+, Airtel. No new real payment or balance operation is authorized for these tests. Carrier-dependent milestones remain unverified, not PASS from fixtures. Earlier QR/manual/readiness evidence is historical and is not silently counted as a fresh check of this build.

Final debug is installed; the complete suite and fresh preparation checks passed, detailed below. No new actual carrier amount/review/PIN/final-response screen has been opened. No input-acceptance screenshot exists for those blocked milestones. No PIN/OTP, recipient details, amount or balance will be requested in chat or captured in logs.

## Real UNKNOWN attempt and external dependencies

The existing exact local ID/UTC timestamp are already recorded privately in build/private-attempt-audit-before.json. No provider reference or provenance exists. PaymentIntegrityPolicy.confirmationAvailable remains false; SUCCESS/FAILED transitions are rejected. No authorized adapter was discovered/configured; AUTHORIZED_CONFIRMATION_CONTRACT.md remains applicable.

Original-transaction reconciliation requires authorized bank/PSP access covering this actual *99# transfer and an authenticated, unambiguous mapping from the local attempt to the provider's actual transaction reference and final status. Matching amount/time/recipient, carrier success wording, a user acknowledgment, QR name, screenshot or balance change cannot establish that mapping or release the reservation. Credentials or PINs must not be provided in chat or placed in the APK.

The unresolved record/reservation must remain protected. No delete/reset/relabel, no replacement attempt, no new live payment to work around it. Until authorized reconciliation and a controlled carrier test are possible, the product's complete end-to-end success definition remains BLOCKED. No production-readiness claim.

### Device interruption

The user confirmed Accessibility OFF/unlocked before testing. App replacement succeeded and the read-only real-attempt audit passed. The test APK compiled successfully, but the later install/run returned device-not-found and adb devices listed no connected device. Therefore zero cases from the new Android run are counted as executed, and this is a device-availability blocker, not a tested application regression. The user was asked to reconnect for the full 42-case run and fresh QR/manual preparation checks. Do not use the old 36-case result as acceptance of the new carrier path.

### Regression investigation (not a PASS claim)

After USB reconnection the first complete Android run executed all 42 cases in 61.64s: one new confirmation UI test asserted callback count 1 but observed 0; other cases passed. That exact assertion/test was left unchanged and passed in isolation (1.735s). After installing the final exact-address matching build, a complete rerun failed 10 UI cases, including existing cases with No Compose hierarchies found. Direct metadata afterward showed an awake/unlocked phone with another application foreground and Accessibility still OFF. This does not prove the cause of each failure. The user was immediately informed and asked for a short uninterrupted run; no tests were removed, skipped or weakened, and no live carrier action occurred. Logs: build/core-journey-android-full.log, build/core-journey-confirmation-repro.log, build/core-journey-android-final.log.

### Final automated acceptance

After the user confirmed an uninterrupted unlocked-device window, V4SH was brought to foreground and all 42 tests passed in 55.61s. The exact earlier callback-count assertion remained unchanged and passed in the full suite, as did the existing UI tests. The change in test conditions and foreground metadata are recorded; the exact cause of the initial missed click/hierarchy losses is NOT ESTABLISHED. Those failed attempts remain in the report/logs, not discarded as passes. No automatic test retry wrapper, assertion relaxation or excluded test was added.

Final JVM: 105/105 (88 prior + 17 new). New cases comprise 13 CarrierPaymentFlow cases, 3 QR cases and 1 added existing-policy case rejecting embedded/multiple candidate recipient addresses. Exact matching now rejects a review containing another VPA rather than accepting the intended address merely because it appears somewhere on the screen.

Final real-store audit PASS: build/core-journey-attempt-after-tests.json matches the original exact local ID, UTC creation timestamp, UNKNOWN state, reservation and absent-reference metadata. Both protected journal and integrity-seal bytes are unchanged from build/core-journey-storage-before.sha256 to build/core-journey-storage-after-tests.sha256. No real carrier interaction, PIN entry or new payment occurred.

Signed release SHA-256: a2a5a5841397fb0f14117bcd02746ba28552c62727d948f80cb09bf997937cdb. Built but not installed over the differently signed debug app; existing data was preserved.

## Final physical preparation evidence

All actions below occurred on the final installed debug build, with no Pay or Check Balance tap. User confirmations contain no payment values or authentication secrets.

| Action | Actual V4SH result | Actual carrier screen/input | Accessibility | Result |
|---|---|---|---|---|
| Restore service through Android settings, open Profile → Payment Readiness | Enabled, connected, instance available (user confirmation) | None; no carrier operation requested | Android independently lists V4SH bound/enabled, not crashed, after preparation checks | PASS |
| Scan first supported UPI QR and reach Review | Correct ID, amount, INR and local note where present; no QR name asserted verified | None; carrier input not attempted | Same restored service remains listed bound at final check | PASS |
| Scan a different supported QR and reach Review | Its own correct details; no stale first-QR details | None; carrier input not attempted | Bound at final check | PASS |
| Enter invalid UPI ID, then valid ID/amount and reach Review | Invalid syntax blocked; valid details correct; beneficiary verification unavailable; earlier unresolved warning and Pay blocked | None; carrier input not attempted | Bound at final check | PASS |
| Carrier initiation/amount/confirmation/PIN/outcome | Not executed: immutable UNKNOWN reservation forbids another real payment | NOT TESTED / BLOCKED; no new actual carrier response, input acceptance or PIN interaction exists as evidence | Preparation binding is not carrier-payment readiness | BLOCKED |

No screenshots of payment details or authentication were captured; physical preparation evidence is explicitly user-reported plus direct system binding/storage metadata. The synthetic UI/protocol tests remain separate and do not count as a real carrier PASS. The app/service were left running without another installation, force-stop or instrumentation restart after restoration.

## Final preservation and decision

PASS: exact private original attempt metadata remains unchanged; pre-install versus final journal and seal SHA-256 values match byte-for-byte, including after fresh preparation. Files: build/core-journey-storage-before.sha256, build/core-journey-storage-final.sha256, build/core-journey-attempt-after-tests.json. The original UNKNOWN attempt/reservation was not deleted, relabelled, cleared or bypassed.

Implemented and tested independent core-journey work is complete for this iteration. Milestones 1 and 2 have automated and fresh preparation evidence; milestone 3 remains BLOCKED by the real reservation and absent authorized reconciliation, and no subsequent live carrier milestone was attempted. Milestones 4/5/6 have guarded code and explicitly non-settling fixture evidence only. Actual foreground/dialog retention, carrier input acceptance, carrier confirmation layout and manual payment authentication remain unqualified. Do not declare the complete core payment journey finished or production-ready. The next live prerequisite is authorized reconciliation of the original transfer, followed by an explicitly authorized controlled carrier test; neither can be substituted by a new payment or manual acknowledgment.

For that preceding iteration, no application-source changes were made after its 105-JVM/build/lint verification. No test assertions were removed or weakened. No real payment, commit or push was performed.


## Focused simplification: automatic carrier amount — 2026-11-04

Implemented the smallest missing component: the carrier amount prompt no longer pauses for a second V4SH amount entry. Both existing QR and manual UPI preparation converge on the same VM/engine/owned command and now use its immutable reviewed amount automatically. No changes to QR scanning, UPI ID preparation, journal/history, analytics, provider integrations, beneficiary resolution, profiles, support, permissions or financial policy.

| Interaction | Production wiring | Verification |
|---|---|---|
| QR/manual preparation and one pre-Pay amount entry | Existing shared VM/engine path retained | Existing preparation tests retained; prior physical preparation evidence above is historical, not a new live transfer |
| Supported root/method menu and UPI ID population | Existing AccessibilityService ACTION_SET_TEXT/Send path, driven by observed labels | Isolated protocol tests; real payment menus remain physically unverified |
| Amount population | Now automatic on a fresh supported editable amount prompt, using the frozen command amount; duplicate/in-flight prompts do not repeat the write; refresh must still show an editable amount prompt before Send | New/revised reducer and non-settling UI tests PASS; no extra amount field/button; actual Airtel field acceptance NOT TESTED |
| Amount acceptance and carrier confirmation | Existing exact recipient/amount review check; explicit user confirmation retained | Fixture tests PASS, not physical carrier evidence |
| PIN and final response | Existing manual carrier PIN boundary and truthful result handling retained | No PIN read/capture/automation; no live payment authentication or fabricated result |

Changes are limited to CarrierPaymentFlow.kt, FestPayAccessibilityService.kt, AccessibilityConnection.kt, PaymentViewModel.kt, CarrierPaymentJourneyScreen.kt and the two existing CarrierPaymentFlowTest/CarrierJourneyUiTest files. Removed the obsolete second-amount-entry callbacks. The pending-input guard preserves an offered action until its OS result, without offering a duplicate write; a sensitive prompt still preempts it. No second engine or session manager.

Tests: **107 JVM PASS** (105 prior plus two new automatic-amount safety cases; obsolete duplicate-entry expectations updated to the explicitly requested one-entry UX). **42 Android PASS**, complete uninterrupted run in **52.255s**, including existing QR/manual, session/consent, guard, exact-review/confirmation and PIN-boundary cases. The amount UI case now asserts zero editable fields and no second amount/early confirmation button. No failing assertion was suppressed. Logs: build/payment-auto-amount-final.log and build/payment-auto-amount-android.log. Debug/test/signed release builds and release lint PASS (0 errors, 40 warnings, 2 hints).

Physical Redmi/Airtel result: final debug app installed without clearing data; isolated Android suite passed on the phone. User restored Accessibility and reported enabled/connected/instance available; Android independently lists V4SH bound/enabled, not crashed. No live carrier menu, amount field, confirmation or PIN was opened because the protected UNKNOWN reservation still forbids a new real payment. Therefore automatic carrier amount input/acceptance is implemented and fixture-tested, **not physically verified**. No new real payment or balance operation was initiated.

Protected record: pre-update and final journal/seal SHA-256 values match byte-for-byte (build/payment-auto-amount-storage-before.sha256 versus build/payment-auto-amount-storage-final.sha256). The original UNKNOWN attempt and reservation remain untouched. Service left connected. No commit/push. Complete live end-to-end payment is not claimed.

Current focused-update signed release SHA-256: aceeba3674b6db95b5cf3093665f5d3b4e59b6c1ac94b2218a86ab35c3e1dab2. The release was built, not installed over the differently signed debug app.


## Standard *99# sequence support: remark skip and PIN-prompt details — 2026-11-05

**Gap found.** The reducer could only finish if the carrier showed a separate "confirm/review" screen before authentication. The publicly documented *99# Send Money flow has no such screen: after the amount it asks for an optional remark ("Enter 1 to skip"), and the UPI PIN prompt itself states the amount and payee. On that sequence the old code stopped at the remark prompt (UNSUPPORTED), and a PIN prompt straight after the amount stopped as UNEXPECTED_AUTHENTICATION. This means the journey could never reach the PIN handoff on the documented flow. Nobody has observed the real Airtel payment prompts yet (milestone 3 is still BLOCKED), so this is support for the documented shape, not a confirmed Airtel layout.

**Implemented, inside the existing reducer/service only (no new engine, permission or dependency):**

| Change | Behaviour |
|---|---|
| `UssdNavigationPolicy.remarkSkipDigit` | Returns a digit only when a remark/note prompt contains exactly one explicit carrier skip instruction ("N to skip" / "N. Skip"). Rejects prompts that mention PIN/OTP/amount/UPI ID/confirm, and rejects two different skip digits. No remark text is ever invented. The local QR note is still not sent. |
| `CarrierInputKind.REMARK_SKIP`, `CarrierStage.REMARK_SENT` | Offered only in AMOUNT_SENT, on an editable prompt. Single-use: a repeated remark prompt never replays it. A failed submission → INPUT_UNCERTAIN, with no retry. Revalidated against fresh labels before SET_TEXT and again before Send (`matchesInput`). A remark prompt before the amount stays out of order → UNSUPPORTED. |
| `UssdNavigationPolicy.authenticationPromptDetails` | Compares the PIN prompt's static instruction labels (never the PIN field) with the immutable request. Results: MATCHING (exact VPA plus a single exact INR amount), AMOUNT_ONLY, ABSENT, MISMATCH (any other VPA, a different amount, or more than one amount, e.g. a fee). |
| `CarrierPaymentFlow.authenticate` | AMOUNT_SENT/REMARK_SENT → PIN is allowed only for a supported UPI PIN instruction. MISMATCH stops assistance. MATCHING records the observed review and `amountAccepted`. ABSENT reaches PIN as **not verified**, and the UI says so. The existing REVIEW → PIN path and every earlier-stage unexpected authentication are unchanged. Repeated PIN frames keep the first observation. |
| Service | Password-flag path passes static instruction labels; REMARK_SENT is treated as an owned carrier window. |
| Journey UI | New REMARK_SENT text. The PIN stage shows whether the carrier's PIN prompt matched the reviewed UPI ID/amount, matched the amount only, or showed neither. It states that entering the PIN on the carrier screen is the user's confirmation. Still no editable field, and the outcome stays UNKNOWN. |

Unchanged: no PIN read, capture or automation; carrier wording never authorizes SUCCESS/FAILED; no automatic retry; no second `*99#` request; immutable review snapshot; reservation and the existing UNKNOWN attempt.

**Tests (automated, synthetic — not carrier evidence):**

| Check | Result |
|---|---|
| JVM suite | PASS: 130 tests, 0 failures/errors/skips (build/standard-flow-jvm.log). New: StandardUssdFlowTest, 10 cases covering the full sequence to manual PIN, password-flag PIN path, PIN directly after amount, amount-only and absent details, wrong VPA/amount/extra fee/look-alike VPA, skip ambiguity and unsafe wording, read-only and out-of-order remark prompts, single-use skip and failure without retry, revalidation, and a review screen after a remark. The first run failed one new ambiguity case ("Press 1 to skip or 2 to skip" accepted "1"). The policy was tightened to count every "N to skip"; the test was not relaxed. Existing tests unchanged. |
| Debug, test APK and signed release builds | PASS (build/standard-flow-build.log). Release SHA-256 5512135fb64cf761066f7324f82fd5442ab375a4a95a8be97395619a6cc49917; built, not installed. |
| Release lint | PASS: 0 errors, 40 warnings, 2 hints (same counts as the previous iteration). |
| Android suite | PASS: 52/52 in 65.5s on the Redmi Note 13 Pro+ (unlocked, V4SH in the foreground), via `adb install -r` + `am instrument`, with data preserved (build/standard-flow-android.log). Includes the new CarrierJourneyUiTest case: PIN-prompt matched / not-verified text, REMARK_SENT text, zero editable fields. Synthetic fixtures only. |

**Physical:** debug build installed on the Redmi Note 13 Pro+ without clearing data. The journal DB SHA-256 (3bdffb92…8dfb) and all integrity-seal hashes were identical before install and after the full Android run (build/standard-flow-storage-before.sha256 vs build/standard-flow-storage-after-tests.sha256). No carrier menu, remark prompt or PIN prompt was opened. The real UNKNOWN reservation still forbids a new live payment. Remark-skip and PIN-prompt matching are therefore **implemented and fixture-tested, not physically verified**. Updating the APK may unbind V4SH from Android Accessibility; re-enable it in Settings if Readiness shows it disconnected.

Milestone status after this change: 3 BLOCKED (unchanged). 4 PARTIAL: amount acceptance can now also be observed from the PIN prompt. 5 PARTIAL: documented no-review sequence supported, native confirmation path unchanged. 6 BLOCKED for physical verification; the code path to the manual PIN handoff now exists for the documented sequence. No commit or push.


## Productionization pass 1 — 2026-11-05

### Defect fixed: PIN entry could be cut off by the navigation timeout

A single 120 s clock started when Pay was tapped and was never re-armed. A user who was still entering their UPI PIN when it expired got "input uncertain" (a failed-submission message) and the result observation stopped.

| Change | Detail |
|---|---|
| `FestPayAccessibilityService.scheduleOperationTimeout` | One pending timeout per session. The first PIN observation re-arms it to 300 s (`PIN_RESULT_TIMEOUT_MS`). The navigation window stays 120 s. Check Balance behaviour is unchanged (same 120 s, same `markSubmissionUnclear`). A timer is never treated as acceptance or completion. |
| `CarrierIssue.TIMEOUT` | At PIN expiry the flow becomes INTERRUPTED/TIMEOUT. The outcome stays UNKNOWN, later "successful/failed" wording is ignored, and nothing retries. The journal stores the issue name only, so older events remain readable. |
| Journey UI | Dedicated TIMEOUT text: outcome UNKNOWN, check the carrier screen and bank history, do not pay again. |
| Regression test | `StandardUssdFlowTest.timeoutAtPinKeepsOutcomeUnknownAndIgnoresLaterWording` |

The service timer itself (Handler plus real AccessibilityService) is **not unit-testable here and was not exercised physically**. Only the reducer behaviour is covered.

### QA results (this pass)

| Check | Result | Evidence |
|---|---|---|
| JVM suite | PASS: 131 tests, 0 failures/errors/skips | build/productionize-1.log |
| Debug, test APK, signed release builds | PASS | same log |
| Release lint | PASS: 0 errors, 40 warnings, 2 hints | warnings are 12 outdated-dependency, 8 UseTomlInstead, 7 unused resources, 5 UseKtx, plus minor items. None is a functional defect. |
| Android suite on Redmi Note 13 Pro+ | PASS: 52/52, installed with `-r` (data kept), device unlocked | build/productionize-android.log |
| Protected journal and seals | PASS: SHA-256 of the DB and all seal files identical before install and after the full run | build/productionize-storage-before.sha256 vs -after.sha256 |
| Release APK | SHA-256 3d8bc4dbb376947792d0def367d99e66bf76d99d8efd50c35a36f9e47ef8ecc2. Built, not installed over the debug app. |
| Signature | apksigner verifies. Signer CN=V4SH, cert SHA-256 a519fdf2…0003b. Signing configuration untouched. |
| 16 KB page alignment | PASS: `zipalign -c -P 16` OK on every bundled `.so` |
| Offline scanning | PASS (static inspection): the release APK contains `libbarhopper_v3.so` and the `mlkit_barcode_models` assets, so scanning does not need a model download |
| Manifest and permissions | The APK declares CALL_PHONE and CAMERA, with INTERNET explicitly removed. Transitive extras: ACCESS_NETWORK_STATE (unused by app code; left, because removing it could make a library throw) and a signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` from AndroidX. allowBackup=false with exclusion rules. MainActivity is the only exported activity. The AccessibilityService is exported but guarded by BIND_ACCESSIBILITY_SERVICE. |
| Dependency advisories | 154 resolved release-runtime artifacts queried against the public OSV database: 0 advisories (build/osv-hits.json). OSV's AndroidX coverage is incomplete, so this is a screen, not a guarantee. A Maven coordinate list (no code or user data) was sent to api.osv.dev. |

### Distribution and platform assessment (not a legal opinion — verify against current policy text)

- **Google Play is a high-risk route.** V4SH uses an AccessibilityService that is not an accessibility tool (`isAccessibilityTool=false`) to read system dialogs and enter menu choices and amounts into a financial flow. Google's policy limits non-accessibility use of the API and restricts apps that use it to act on the user's behalf. Approval would need the Play Console accessibility declaration and a prominent in-app disclosure. The disclosure exists (MainActivity dialog) but must be checked against the policy wording. Rejection is plausible, and I am not claiming eligibility.
- **Direct APK distribution** works technically, but on Android 13+ sideloaded apps need the user to allow "restricted settings" before they can enable an AccessibilityService. That is a setup step to document for users.
- **Supported alternative.** The QR/UPI-ID preparation and the Review screen work without Accessibility, so a reduced build that hands the user to the dialer without automation is the policy-safe fallback. It is not built, because it was not requested.
- **Not done.** The service config has no `android:packageNames` restriction. The code already filters on source package and system-app status, but a manifest-level filter would limit which apps' events the service receives. I did not change it, because a wrong package list would silently break Check Balance/payment detection and I cannot verify that against a live USSD session here. Recommended once a live test is authorized.
- **Dependencies** (navigation 2.7.6, CameraX 1.4.1, activity 1.8.0, lifecycle 2.6.1, BOM 2026.02.01, and others) are behind current versions. No advisory was found, and a bulk upgrade without a live-flow regression was judged riskier than staying put.

### What is still not done or not verified

- **Physical carrier journey: BLOCKED.** The UNKNOWN reservation forbids a new payment, so no live menu, remark prompt, PIN prompt or return-to-V4SH was observed. Carrier input acceptance and the PIN handoff are fixture-tested only.
- **Authoritative SUCCESS/FAILED: BLOCKED.** No authorized bank/PSP adapter exists. Outcomes stay UNKNOWN.
- **Check Balance** was not run live (not authorized this pass). Shared-service changes in this pass touch only the payment path and the timeout scheduling shared with it.
- **Not tested on this pass:** rotation, process death, force-stop and permission revocation beyond what the existing 52 Android tests cover.

The application is **not declared production-ready**.


## End-to-end feature test and fix pass — 2026-11-05 (physical device, UI driven over adb)

Method: debug build installed on the Redmi Note 13 Pro+ with data kept; each screen opened and read through UI-Automator dumps and screenshots. No Pay, no Check Balance and no carrier action was performed. Synthetic UPI IDs only. Any content from other apps that appeared on screen (a notification) was not used or recorded.

### Defects found on the device and fixed

| # | Defect (observed) | Fix | Verified |
|---|---|---|---|
| 1 | Home showed **Check Balance twice**, plus two separate "Pay by UPI ID" controls | One Check Balance card; one Pay with UPI ID card | device |
| 2 | Home's "Pay with UPI ID" card opened **"Your UPI details"** (profile editor), not payment | Routed to the real payment flow (`upi_payment`) | device |
| 3 | Home and Transactions used a grey background, not the brand cream | Brand cream `#FFF8F0` | device |
| 4 | Greeting was hard-coded "Good morning" at any hour, and could read "Good morning, ." or use a developer's name | Time-aware greeting; no name when none is saved | device + unit test |
| 5 | Home had no real readiness status | Readiness card (ready / setup needed / paused) that opens Payment Readiness; says "Payments paused" while the earlier attempt is unresolved and never claims carrier support | device + unit tests |
| 6 | Bottom-nav labels were monospace, "Transactions" wrapped mid-word, "Profile" clipped | Single-line 10sp labels | device |
| 7 | "Libre Caslon Text" was mapped to monospace and body to sans; most text styles used the system default | All roles map to a serif family; every Material text role now has an assigned family (see open item on the real font files) | device |
| 8 | Home cards had a fixed height that clipped "Show your UPI QR" | Equal, content-sized heights | device |
| 9 | Pay by UPI ID was an unthemed white page with no top bar. It showed raw enum text ("INVALID_UPI_ID", "UNAVAILABLE"), "Name unavailable" under an invalid ID, and a "Check beneficiary name" button that could never succeed | Themed screen with top bar; plain-language messages; dead button removed (the shared preparation still resolves recipients if an authorized provider is ever configured) | device |
| 10 | Amount screen gave no reason for a rejected amount; the "Standard UPI" pill collided with the name | Inline messages (e.g. "Use at most 2 decimal places."); layout fixed | device + unit test |
| 11 | Review / Pay screens showed "Recipient verification: UNAVAILABLE" | "Recipient name not verified". The existing Android assertion for that string was updated to the new wording; its other assertions are unchanged | device + Android suite |
| 12 | Transactions: timestamps read "02 Nov 2026, 14:02:42 GMT+05:30"; details showed raw UNKNOWN / NO_AUTHORIZED_CONFIRMATION; a saved name was presented as "Payee"; a blank saved name gave an empty row title | Short local time, plain outcome wording, "Name (unverified)", UPI ID fallback for a blank name | device + unit tests |
| 13 | Receive: QR and text were left-aligned in the card; no way to share | Centred; added a "Share UPI ID" action (standard Android share sheet, no permission) | device |
| 14 | Payment Readiness exposed internals ("Session initialized: false; acknowledgment received: false") on an unthemed page | Themed checklist with a verdict | device |
| 15 | After the app was reclaimed in the background, the Recipient/Amount screens stayed open with a blank recipient (Review then refused safely) | "Start again" notice with a Back to Home button; no payment is created | device (`am kill` + restore) |
| 16 | Payment journey and SIM guidance screens were unthemed; no progress indicator | Cream background and top bar; a progress bar only while a carrier step is genuinely pending (never at PIN or after a result) | build + Android suite (visual check pending a live journey) |

### Verified working (no change needed)

Scanner screen opens with camera permission granted and "Camera Active"; manual UPI ID → Amount → Review with correct recipient, INR amount and "Pay" blocked by the earlier unresolved attempt; Transactions list and details; Receive QR for the saved ID; Profile, App Security, Privacy, Help Center, Contact Support, Check Balance entry screen.

### Results

| Check | Result |
|---|---|
| JVM suite | PASS: 138 tests, 0 failures (7 new in `UiPresentationTest`) |
| Android suite on the Redmi | PASS: 52/52 (build/e2e-android.log), data kept |
| Protected journal and seals | SHA-256 identical before install and after the full run (build/e2e-storage-before.sha256 vs after) |

### Open or not verified

- **Payment Accessibility service is OFF on the phone.** Each reinstall unbinds it. Android requires the user to switch it back on; V4SH now says so on Home and in Payment Readiness.
- **Brand font files are not bundled** (Bodoni Moda, IBM Plex Serif, Libre Caslon Text). All roles use a system serif until they are added; that needs the font files downloaded (SIL OFL, from Google Fonts), which has not been approved.
- **Camera scanning** of real QR codes was not driven by adb. Six synthetic test codes (build/qr-tests) are ready; results are recorded separately once scanned.
- **Live carrier flow, Check Balance and real payment results**: not run (earlier UNKNOWN reservation; Check Balance needs the user to enter their PIN).

### Camera scan and QR rejection pass (physical + automated)

- **Physical camera:** with the phone held at a QR on a screen, the scanner decoded a real UPI QR, opened Recipient ("Name from QR — unverified", "Recipient name not verified", "Scanned from UPI QR"), Amount, and Review (Pay blocked by the earlier unresolved attempt). "Scan Again" returned to a live camera. The scanned code was the user's own UPI QR, not one of the six synthetic test codes, so the synthetic-code results below are automated, not camera-based. An earlier black preview was a covered lens: frames were flowing and the log showed no camera error.
- **Defects found by reading the code and fixed:** (17) the "not recognized" screen printed the raw enum (`Format check: MALFORMED_URI`); (18) the parser's rejections never reached the dedicated "unsupported" and "details missing" screens, so every rejection landed on one screen. Now: non-UPI/unsupported scheme and non-INR currency → Unsupported; missing recipient → Details missing; everything else → Not recognized. Each shows a plain reason.
- **Tests:** JVM 140/140 (adds `QrRejectionWordingTest`: five rejected payloads each map to a distinct issue with a plain label, plus two valid codes with and without an amount). Android 53/53 on the Redmi (adds `QrRejectionRoutingTest`: routing for each rejected payload, and the draft is cleared on rejection). Protected journal and seals identical before and after (build/e2e2-storage-before.sha256 vs after).
- **Not camera-tested:** the rejection screens for codes 3–6 were not reached through the camera; the pane showing them sat behind the app window and could not be confirmed.

### Brand fonts bundled — 2026-11-05 (download approved by the user)

- **Source and licence:** Google Fonts repository (`github.com/google/fonts`, `ofl/` folders), SIL Open Font License 1.1. Licence texts are in `docs/licenses/` (OFL-BodoniModa.txt, OFL-IBMPlexSerif.txt, OFL-LibreCaslonText.txt).
- **Files (sizes matched the repository listing exactly; all valid TrueType):** `BodoniModa[opsz,wght].ttf` 162,104 B; `IBMPlexSerif` Regular 163,068 B, Medium 164,104 B, SemiBold 164,528 B, Bold 163,800 B; `LibreCaslonText[wght].ttf` 126,876 B. Total about 0.94 MB. Italics were not downloaded because the app uses none. They were fetched into an empty folder (`build/fonts-download`), checked, then copied to `app/src/main/res/font/`.
- **Wiring:** `ui/theme/Type.kt`. Bodoni Moda → headings and titles; IBM Plex Serif → body; Libre Caslon Text → labels. The two variable fonts pin a weight per entry (400–700); Bodoni uses optical size 28sp. The glyph for ₹ falls back to the system font if a family lacks it.
- **Evidence:** debug and signed release builds pass. The release APK contains all six font files and is signed by the same V4SH certificate (a519fdf2…0003b), release SHA-256 c9717637d65118fb2a6b1b5345d770e03d530168747ea8b1a7b921c1fd1770bc. JVM 140/140. Release lint 0 errors, 40 warnings, 2 hints.
- **NOT verified:** how the fonts look on the phone, and the Android suite with the fonts, because the Redmi had locked itself (secure lock screen) before the check. No rendering claim is made until it is looked at. Possible visual risks to check: Bodoni Moda at 24sp headline weight, and the nav-label width in Libre Caslon (10sp).


### Font weights fixed — 2026-11-05 (measured on the Redmi)

- **Defect:** after bundling, Bodoni Moda and Libre Caslon Text drew Bold identically to Normal (so "bold" headings and labels looked regular). IBM Plex Serif was fine. Cause: both files are variable fonts, and Compose's `res/font` loading did not apply their weight axis on this device. The files themselves are correct (fvar axes: Bodoni wght 400–900 and opsz 6–96; Caslon wght 400–700), and a direct platform probe confirmed Android applies the axis when a font is loaded from a file (Caslon ink 6,896 → 10,068; Bodoni 5,026 → 7,862 for wght 400 → 700).
- **Attempts that did not work (all measured, normal = bold ink):** Compose `variationSettings` on `res/font` entries; font-family XML with `fontVariationSettings`; separate resource copies per weight.
- **Fix:** the two variable fonts moved to `assets/fonts/` and are loaded by path (`brandFonts(assetManager)` in `ui/theme/Type.kt`, applied in `V4SHTheme`), with weights 400–700 and Bodoni optical size 28. IBM Plex Serif stays in `res/font`.
- **Regression test:** `FontWeightRenderTest` renders each family at Normal and Bold and requires at least 12% more ink at Bold. All 3 pass on the device. The temporary probe test was deleted after use.
- **Results:** JVM 140/140; signed release builds; release lint 0 errors, 40 warnings, 2 hints; release APK signed by the same V4SH certificate (a519fdf2…0003b), SHA-256 d614fcc02f7737f0db5dc1e1e46d41d26a0e3d841b06b44858e53cdd51cb13b2, containing both fonts under `assets/fonts` plus the four Plex files.
- **Not yet verified after this change:** a visual look at the screens and the full 53-test Android suite (the phone locked itself). The earlier full suite run (53/53) was with the previous font loading. Layout risk to eyeball: heavier Bodoni headline widths and the 10sp nav labels.

### Final verification of the font build — 2026-11-05 (physical device, unlocked)

- Visual check on the Redmi: bold Bodoni Moda headings and bold Libre Caslon labels now render at real bold weight; body text is IBM Plex Serif; ₹ renders (system fallback for the glyph); all four bottom-nav labels fit; the inline amount error shows. One flaw found and fixed: the placeholder name "Name unavailable" was ellipsized next to the "Standard UPI" pill; the name now wraps to two lines.
- JVM 140/140. Android suite on the Redmi: **56/56** (53 earlier plus 3 `FontWeightRenderTest` cases). Debug, test and signed release builds pass; release lint 0 errors.
- Protected journal and seals: SHA-256 identical before and after the full run (build/fontsfinal-storage-before.sha256 vs build/final-storage-after.sha256).
- Release APK SHA-256 e50e0c9f1884a63e7edc5e49152006538d818bf7af656f4315dac80e582a9493 (same V4SH signing certificate; built, not installed).
- Still not done: real payment, real Check Balance (needs the user's PIN) and any live carrier step; the V4SH Accessibility service is switched off after the reinstalls and must be re-enabled by the user.

## Reference project study (NoInUPI) — 2026-11-06

Full comparison, licence finding, adopted/rejected items and the open decision are in `docs/REFERENCE_NOINUPI_COMPARISON.md`.

- **Licence:** the supplied ZIP has no licence file, so nothing was copied; three behaviours were reimplemented independently: ignoring transient "Processing/Loading/Connecting" frames, stopping on pre-authentication carrier rejection wording (`CarrierIssue.CARRIER_REJECTED`), and submitting via exact Send/Reply/Submit/OK controls.
- **Rejected from the reference:** SUCCESS/FAILED from carrier text, inferred transaction references, auto-confirm, plaintext SharedPreferences history.
- **UI:** no screen, layout, colour, navigation or text was changed; the existing "Assistance stopped: …" sentence now shows "carrier rejected" for that case.
- **Results:** JVM 148/148 (8 new in `ReferenceInformedBehaviorTest`); Android suite on the Redmi 56/56; debug, test and signed release builds pass; release lint 0 errors / 40 warnings / 2 hints; release APK signature verifies (cert a519fdf2…0003b), 16 KB alignment verified, permissions unchanged (CALL_PHONE, CAMERA, transitive ACCESS_NETWORK_STATE; no INTERNET); protected journal and seals identical before and after (build/ref-storage-before.sha256 vs ref-storage-after.sha256). Release APK SHA-256 e325c46ef1b665d950be9d9123ce62696f9fa5bbd32f4d31f2168a710fa98497 (built, not installed).
- **Evidence level:** fixtures and a physical device test run only. No carrier interaction, no live payment, no authoritative result.
- **Distribution variants:** the project has no separate Direct and Play product flavors (single `app` module, one manifest); none were created in this pass. Play approval of the Accessibility use remains an owner/Google decision (see DISTRIBUTION_READINESS.md).

## Review → USSD initiation failure trace — 2026-11-06

**Call chain (real names):** `ReviewPaymentScreen` (ScreenPlaceholders.kt) → Pay `Button(enabled = isPayButtonEnabled && reviewCurrent && !paymentViewModel.paymentPreparationBlocked)` → `onClick` → `PaymentViewModel.startPayment` → guards → `UssdSessionTransport.prepare` → `PaymentAttemptJournal.create` / `transition(PROCESSING)` → `RealUssdPaymentEngine.executePayment` → `UssdSessionTransport.arm` → `Context.startActivity(Intent(ACTION_CALL, tel:*99%23))`.

**First failing operation (physical Redmi, debug build with the new diagnostics):** `PaymentViewModel.inspectPaymentReadiness`, run when Review is shown, calls `PaymentAttemptJournal.unresolvedId()`, which returns the earlier attempt (`6f6aaf1f`). It sets `operationError`, `preparationBlock = UNRESOLVED_ATTEMPT` and `paymentPreparationBlocked = true`. The Review screen then prints that message in red and the Pay button is disabled, so nothing after it runs. Device log: `review.screen SHOWN reviewCurrent=true` → `viewmodel.readiness BLOCK=UNRESOLVED_ATTEMPT payBlocked=true attempt=6f6aaf1f`. No `review.payButton CLICK` exists because a disabled button cannot be clicked. The QR/UPI request itself is intact (`reviewCurrent=true`, same immutable request).

**Responsible: the existing UNKNOWN reservation.** Not released, bypassed, modified or relabelled; the journal and seal hashes are identical before and after every test run (build/pay-storage-before.sha256 vs pay-storage-after.sha256).

**If the button were enabled,** `startPayment` would stop at the *first* failing guard in this order: consent, busy, operation active, invalid request, context, **Accessibility not connected** (currently the case on the phone after the reinstalls), Phone permission, cellular readiness, storage, earlier history, **reservation**, reviewed details changed. Previously guards 4–7 shared one generic message ("Check recipient, amount, Phone permission and V4SH Accessibility"), a silent `return` on "busy" left Pay stuck on "Preparing carrier launch…", and the transport, recheck and engine failures were each one generic message.

**Changes:** `PaymentViewModel.kt` (`StartFailure` enum + `startFailure`, one specific message per guard, `PreparationBlock` already existed; busy now reports instead of returning silently; recheck split into six specific reasons; engine/launch results recorded as `ENGINE_REJECTED` / `LAUNCH_FAILED`; `RealUssdPaymentEngine` messages distinguish "prepared session missing", "saved attempt does not match" and "could not arm Accessibility"), `UssdSessionTransport.kt` (`PrepareFailure` + `lastFailure`), new `data/PaymentDiagnostics.kt` (stage name, outcome, exception class, 8-character attempt prefix only; never UPI IDs, amounts, names or screen text; `Log.i` is stripped from release builds, confirmed by a dex scan), ScreenPlaceholders.kt (two log lines only, no UI change).

**Tests:** JVM 153/153 (5 new in `PaymentStartFailureJvmTest`). Android on the Redmi 63/63 (7 new in `PaymentStartFailureTest`: Accessibility not connected, Phone permission denied, consent withdrawn, invalid request, existing reservation blocking before handshake/engine/launch with the record byte-identical and Pay disabled, unacknowledged session creating no attempt, Android refusing the dial → `LAUNCH_FAILED` with the attempt kept UNKNOWN and reserved). Existing `ReviewProductionEngineTest` proves that with preconditions met the chain reaches the engine and requests exactly one intercepted `ACTION_CALL tel:*99#`. Debug, test and signed release builds pass; release lint 0 errors (40 warnings); release APK signature verifies (cert a519fdf2…0003b), 16 KB alignment verified; release APK SHA-256 6e8acfa9ee80633cf83c5eed7fbf441432eb1995fbac5709f4e15ab261c0d506.

**Not verified:** a real Android USSD launch. The dial was intercepted in fixtures only; the reservation forbids a real one. No carrier interaction occurred.

## Owner resolution of an UNKNOWN attempt — 2026-11-06 (owner-approved)

**Decision (owner, 2026-11-06):** no bank/PSP assurance is required; the owner resolves an UNKNOWN attempt from their own bank record. This supersedes "keep it locked" for the mechanism only; V4SH never resolves anything itself.

- **Journal** (`PaymentAttemptJournal.resolveByOwner`, `OwnerResolution { DEBITED, NOT_DEBITED }`): allowed only for the reserved attempt in state UNKNOWN; atomic with the reservation release inside the existing sealed transaction; idempotent for the same answer, refuses a different second answer; appends an `OWNER_DECLARED_*` event with source `OWNER_DECLARATION`. The attempt's state stays UNKNOWN, no reference or provenance is invented, no SUCCESS/FAILED is ever written (`PaymentIntegrityPolicy` unchanged). `create()`/`unresolvedId()` treat an owner-resolved attempt as closed; `validate()` accepts only consistent declaration fields.
- **ViewModel** (`PaymentViewModel.resolveAttemptByOwner`): refused while a cellular operation is active; refreshes history, readiness and Pay state.
- **UI (one control, existing components):** Transaction Details shows "Resolve this attempt" only for an open UNKNOWN attempt; it opens a standard `AlertDialog` ("Check your bank first") with "Money was debited" / "Money was not debited" / "Cancel". Resolved records show "Resolved by you: money was (not) debited" in the list and details, plus "This is your own statement after checking your bank. V4SH did not verify it." One Help sentence was corrected, because it said the protection could never be released.
- **Tests:** JVM 154/154 (adds wording test). Android on the Redmi 69/69 (adds 6 in `OwnerResolutionTest`, synthetic journals only: unlock + record intact + new payment allowed; idempotent / immutable / survives restart; only reserved UNKNOWN is resolvable; event trail; ViewModel refusal while active then unblock; Details screen control, dialog and result). The user's real journal and seal hashes are identical before and after the full run (build/own-storage-before.sha256 vs own-storage-after.sha256): the real attempt was **not** resolved by anything in this work.
- **Builds:** debug, test and signed release pass; release lint 0 errors; release APK SHA-256 in the final message; signature and 16 KB alignment verified.
- **Not verified:** a real USSD launch or real payment (only the owner can start one).

## First real carrier launch, direct Pay flow — 2026-11-06

**Observed on the Redmi/Airtel (user screenshot):** QR scan → Review → Pay opened the real `*99#` dialog ("Select Option: State Bank Of India 1. Send Money 3. Check Balance 4. My Profile 5. Pending Requests 6. Transactions 7. UPI PIN") but V4SH typed nothing and reported "Assistance stopped: unsupported". This is the first physical evidence that the Pay path launches the carrier (physical launch verified; the owner resolved the earlier attempt first).

**Cause (reproduced with that exact text):** root-menu detection (`CarrierPaymentFlow.rootOption/option`) failed when the dialog text contained non-breaking spaces or repeated lines; either made the unique "Send Money"/"Check Balance" choice look absent or ambiguous, which stops assistance as UNSUPPORTED. The plain text passed; both variants failed. The live dialog could not be inspected afterwards, so which variant the carrier used is not established; both are now handled. Fix: exotic spaces are normalised once in `observe`/`matchesInput`, and a repeated identical choice is not ambiguous (different digits still are). Tests: `RealAirtelMenuTest` (4).

**Flow change (owner request):** the Review page and the after-pages are out of the app. Amount screen now shows the plain reason Pay cannot start and has the Pay button ("Pay ₹x"); tapping it freezes the request, starts the existing journal/engine path (`PaymentViewModel.startPayment`), opens the carrier and returns to Home. Removed from navigation: `review_payment`, `payment_journey`, `payment_in_progress`, `payment_successful`, `payment_failed`, `payment_status_unclear`, `payment_cancelled`, `authentication_required`. The composables are still in the source because five existing instrumented tests reference them; deleting them needs those tests retired. All safety guards, the journal and the owner-resolution control are unchanged. `LocalFlowRegressionTest` was rewritten for the new flow (Pay disabled with the reason shown, no stale QR data, nothing started).

**Results:** JVM 158/158; Android on the Redmi 69/69; real journal identical before/after my run; debug, test, signed release builds pass; release lint 0 errors.
**Not done / open:** the typing of the root choice, UPI ID and amount into the live carrier dialog is not yet verified after the fix; fonts (Freight Text is a commercial typeface and cannot be bundled).

## First successful carrier typing; progress screen and carrier result in history — 2026-11-06

- **Verified by the owner on the Redmi/Airtel:** after the waiting-for-menu fix, V4SH launched `*99#` and typed the menu choice, UPI ID and amount itself ("working perfectly"). This is the first physical verification of the typing path; the PIN stayed manual; no authoritative payment result exists.
- **Progress screen** (`PaymentProgressScreen`, route `payment_progress`): replaces the jump to Home after Pay. It shows the real operational step (menu, Send Money, UPI ID option, UPI ID, amount, "you enter your PIN on the carrier screen", carrier reply), spinner on the current step, plain interruption reasons, and an always-visible note that the carrier's wording is not a bank confirmation.
- **Carrier's last message in history:** `TransactionRecord.carrierResult` (SUCCESS_WORDING / FAILURE_WORDING) is read from the encrypted event trail; rows and details show "Carrier said: success/failed (unverified)". The attempt stays UNKNOWN, no reference is invented, the reservation is unchanged. Two existing tests that compared whole records now compare `original.copy(carrierResult = "SUCCESS_WORDING")`, so only that display field may differ.
- **Results:** JVM 163/163; Android on the Redmi 71/71; real journal identical before/after the run; debug and signed release builds pass; release lint 0 errors; release APK SHA-256 de26bfef223744857590c5751a69b57f830c48d7511c48c3a7f189250d1faf14 (built before the final test-only edit; app code unchanged).
- **Still unverified:** other phones/ROMs, Jio and BSNL SIMs, non-English carrier menus.

## Real success message, carrier reference in history, and speed — 2026-11-06

**Physical evidence (owner screenshot, Redmi/Airtel):** the carrier's final message was "Your payment to <name>, for Rs.1.00 is successful (RefId: 214749357315). Select option to 1. Save contact 2. Exit". The progress screen showed "Stopped" despite that.
- **Why:** (1) after the PIN the carrier closes its dialog for seconds while it processes; V4SH read the gap as the end of the session and stopped observing; (2) the final message ends with an input box, and the PIN-stage code only accepted result frames without one.
- **Fix:** the session is no longer ended by dialog removal while at the PIN stage (the 300 s PIN window still applies); a final message is recognised with or without an input box; `CarrierReference.parse` reads the printed reference only when the message shows exactly one reference and exactly one amount equal to the reviewed amount. The reference is stored in the encrypted event trail and shown in Transaction Details as "printed by the carrier, not verified by your bank" (`referenceProvenance = CARRIER_MESSAGE`). The attempt stays UNKNOWN; the owner still resolves it (owner's choice). Wrong-PIN prompts remain authentication prompts, not results.
- **Tests:** `FinalMessageTest` (5, including the exact real message), `CarrierReferenceHistoryTest` (3).

**Speed (measured on the Redmi):** one journal read (open + unresolvedId + records, 1 attempt, 12 events) took a median **1,242 ms**, because every record was decrypted through the Keystore (and the Keystore was reloaded for each decrypt). The service did that on the main thread for every accessibility frame (about 7 per second), plus again on every window change. Changes: no journal work for a repeated frame (nothing changed); package-manager lookups cached; Keystore key handle kept for the process; decrypted records remembered in memory (bounded, not on disk) with a key-presence check (about 1 ms) before every read so key loss still blocks storage; one pass over events instead of two; the type scale is built once per process rather than per screen. Result: **median 19 ms (first read 95 ms)**. A first attempt that cached without the key check broke the existing key-loss tests (`EncryptedStorageTest`); those tests caught it and the design was corrected, not the tests.
- **Results:** JVM 168/168; Android 74/74 on the Redmi (with V4SH in the foreground; an earlier run hung because WhatsApp was in front, a device-state issue); real journal identical before and after; debug and signed release builds pass; release lint 0 errors.
- **Not measured:** end-to-end frame rates of the progress screen during a live payment; the owner should report whether it feels faster.

## PIN-prompt fix and carrier-verdict history — 2026-11-06 (owner policy)

**Evidence (read-only inspector on the real journal, latest attempt):** the encrypted trail was `CARRIER_METHOD → RECIPIENT → AMOUNT_PENDING → AMOUNT → AMOUNT_SENT → REMARK_SENT → INTERRUPTED(UNEXPECTED_AUTHENTICATION)`. The typing and remark skip worked; V4SH then rejected the carrier's PIN prompt because `isSupportedUpiAuthenticationPrompt` demanded one exact sentence ("enter/provide/type your UPI PIN") and excluded wording like "do not share"/"never". The payment itself succeeded; the final message was therefore never read. (The live log buffer had rolled over; the journal trail is why the cause is known rather than guessed. The exact PIN-prompt wording on the phone is NOT known; the new rule accepts any prompt that names the UPI PIN and is not an OTP/password/CVV/PIN-management/error screen.)

**Owner policy (explicit, 2026-11-06):** the carrier's own final message is the evidence V4SH acts on; V4SH does not query a bank. `CarrierOutcomePolicy`: SUCCESS only if the message says success **and** prints a reference **and** shows the reviewed amount; FAILED if it says it failed; unclear wording decides nothing. `PaymentAttemptJournal.resolveByCarrier` closes the UNKNOWN attempt atomically (reservation released), stores verdict and reference with provenance `CARRIER_MESSAGE`, appends a `CARRIER_MESSAGE_*` event, is idempotent, refuses a conflicting verdict, a success without a reference, a malformed reference, a non-UNKNOWN attempt, or an attempt the owner already resolved. The state machine still never holds SUCCESS/FAILED: the verdict is stored beside the UNKNOWN state, labelled by source. The service calls it when the RESULT stage is reached.
**History:** rows and Details show "Successful · confirmed by carrier" / "Failed · reported by carrier", the reference ("printed by the carrier"), and "Taken from the carrier's own final message. V4SH does not query your bank." No manual resolve control is offered for a closed attempt. The owner-resolve button remains for attempts the carrier did not close.
**Tests:** JVM 176/176 (adds `CarrierVerdictTest` 8); Android on the Redmi 79/79 (adds `CarrierVerdictJournalTest` 5); real journal identical before/after; debug and signed release builds pass; release lint 0 errors.
**Not yet verified live:** that the new PIN rule accepts the Airtel/SBI prompt and that the final message is read and stored on a real payment.

## PIN-prompt mismatch no longer stops observation — 2026-11-06

**Evidence (saved trail, attempt 5928dbe9):** `CARRIER_REMARK_SENT` at 17:14:24.8 then `CARRIER_INTERRUPTED(MISMATCH)` at 17:14:26.7. The next carrier screen was the PIN prompt; V4SH compared its text with the request and stopped. The payment then succeeded ("... successfully added to your beneficiary", then "Your payment ... for Rs.1.00 is successful (RefId: 224434494707)") but nobody was listening, so history showed "Outcome unknown". The previous fix (accepting the real PIN prompt) is what exposed this second check. The exact PIN-prompt text is not known; which detail tripped the comparison is therefore a hypothesis (for example an amount printed more than once), not an established fact.
**Change:** at the PIN prompt a mismatch (different/ambiguous UPI ID or amount) now sets `detailsMismatch`, shows a bold red warning on the progress screen ("check it carefully before entering your PIN, or cancel"), and keeps observing. V4SH cannot block the carrier's PIN screen, and stopping only forfeited reading the result. A SUCCESS verdict still requires the carrier's message to show the reviewed amount and a reference, so a wrong-amount message yields no reference and no verdict. The REVIEW-stage mismatch (a separate confirm screen) still stops assistance, unchanged.
**Test change, stated openly:** `wrongAmountOrRecipientInPinPromptStopsAssistance` encoded the old stop; it is replaced by `wrongAmountOrRecipientInPinPromptIsFlaggedAndNeverTreatedAsAVerifiedMatch` (stage PIN, flagged, never accepted as a match, no review) plus `aMismatchedPinPromptStillAllowsTheFinalMessageToBeReadButAWrongAmountNeverYieldsAReference`. A new test covers the "added to your beneficiary" message arriving before the success message.
**Results:** JVM 178/178; Android on the Redmi 79/79 on the current build; real journal identical before/after; debug and signed release builds pass; release lint 0 errors; release APK SHA-256 1a11d71af97cdd229c00b16b3c7aa114c8fdb4d18ce4e3f055335eac623d4448.
**Not yet verified live:** that the final message is now read and stored. The attempt already stopped (5928dbe9) cannot be filled in automatically; its message showed RefId 224434494707 and success, which the owner can record with "Resolve this attempt".

## Exit option, result screens, auto-off, carrier name, profile letter — 2026-11-09

- **Exit after the final message:** `CarrierInputKind.EXIT`. When the final message arrives with an input box, V4SH types the carrier's own "N. Exit" digit (unique and exact; "Save contact" is never chosen), once, revalidated against fresh text. A failed exit does not change the verdict.
- **Result screens:** success (carrier success message + printed reference + reviewed amount) shows an animated tick, "Payment successful", payee, amount and reference. Interrupted, timed-out, failure wording, or no confirmed success shows an animated cross and "Payment unsuccessful". Per the owner's request "no success message = unsuccessful" is shown on screen and in history ("Unsuccessful · not confirmed by carrier"), but the attempt itself stays open (UNKNOWN) so the owner can resolve it: not seeing a success message does not prove no money moved.
- **Accessibility:** typing into the carrier's dialog cannot be done without an Accessibility service. The service now calls `disableSelf()` 9 s after every cellular session ends, so banking apps are not blocked between payments; it must be turned on again before the next payment. NOT verified on a device (phone was disconnected).
- **Name:** a name cannot be looked up offline from a UPI ID. V4SH now reads the name the carrier prints ("... has been successfully added" / "Your payment to NAME, for ...") into the event trail and shows it in history; a saved/QR-supplied name is never overwritten. The QR `pn` is now saved with the attempt (unverified).
- **Profile letter:** first letter of the name (QR name, else carrier name) in the Amount screen, history rows and the result screen; "?" when none.
- **Tests:** JVM 184/184 (`ExitAndNameTest` 6); one existing assertion in `FinalMessageTest` and the progress-screen Compose test were updated to the new documented behaviour; new `CarrierNameAndConfirmationTest` (3). Device suite NOT run (phone disconnected). Debug and signed release builds pass; release lint 0 errors.

## Carrier's own message on balance failure; SIM chooser — 2026-11-09

- **Observed:** with SIM 2 (Jio) as the default calling SIM, Check Balance returned a "not valid" carrier reply and V4SH showed a generic failure.
- **Carrier's words on screen:** the service now remembers the last non-sensitive text the carrier showed (`CarrierMessage.plain`: no dialog title, one line, at most 300 characters, filler frames ignored) and, in Check Balance, ends at once with "The carrier replied: …" when that text is a plain rejection ("not valid", "connection problem or invalid MMI code", "service not available" …) instead of waiting for the 2-minute timeout. Timeouts and submission failures append "The carrier last said: …". The payment result screen also shows the carrier's last words when unsuccessful. The text is only displayed, never used to decide an outcome.
- **SIM chooser:** a "Calling SIM" row on the Check Balance and payment (Amount) screens opens a dialog: Phone default, or each active SIM ("SIM 1 · Airtel"). The choice is saved (non-sensitive, ordinary preferences) and both `*99#` calls build their intent through `SimSelection.dialIntent`, adding the chosen SIM's calling account when it can be resolved; otherwise Android's default applies. **New permission:** `READ_PHONE_STATE`, requested only when the chooser is opened, solely to list SIMs and find their calling accounts (this supersedes the earlier "no READ_PHONE_STATE" statement in ENGINEERING_DATA_INVENTORY.md / DISTRIBUTION_READINESS.md). The mapping from a SIM to its calling account is matched by subscription id, ICC id or label and is NOT verified on a device (the chooser was not exercised on the phone: the permission is the owner's to grant).
- **Tests:** JVM 187/187 (`CarrierMessageTest` 3); Android on the Redmi 85/85 (`SimSelectionTest` 3); real journal identical; debug and signed release builds pass; release lint 0 errors.
- **Not verified:** that the chosen SIM actually places the call on this ROM; Jio's behaviour for `*99#` (it replied "not valid" as the default SIM).

**Correction to the section above:** when it was first written, release lint had actually FAILED (2 `MissingPermission` errors in `SimSelection`, which lint could not see were guarded by `hasPermission()`), but the section said "0 errors". Both functions are now annotated `@SuppressLint("MissingPermission")` next to their guard; release lint then passed with 0 errors, 40 warnings, 2 hints. Final signed release APK SHA-256 010632a1fd155e0512a0fe449488082f33408aeaa3e819c748cc6bc7054cf126 (signature and 16 KB alignment verified). The debug build installed on the phone differs from it only by those two annotations.

## SIM choice fix, Sleep mode, auto-off — 2026-11-09

- **SIM choice (device evidence):** the earlier chooser saved the choice but the call still used SIM 1, because finding the SIM's calling account needs READ_PHONE_NUMBERS, which V4SH does not ask for. The handle is now built directly (telephony service + subscription id, the form `dumpsys telecom` shows). Live on the Redmi: choice 2 gave Jio's "Connection problem or invalid MMI code."; choice 1 gave the SBI menu.
- **Sleep mode (Profile > Sleep mode):** shows awake/asleep, an "auto-sleep after each session" switch (default on), and "Put V4SH to sleep now" (`disableSelf()`; refused while a carrier session is running). Waking is done by the owner in Android Accessibility settings; V4SH cannot turn itself on.
- **Auto-off:** the 9 s is counted from the END of the session (result, exit or stop), not from when the carrier dialog opens. With auto-sleep off, the service stays on until put to sleep.
- **Permission change (stated openly):** READ_PHONE_STATE is now declared and requested only when the owner opens the SIM chooser. Docs that said "no READ_PHONE_STATE" describe readiness only. `ReadinessConsentTest.publicReadinessObservationNeedsNoNewRuntimePermissions` no longer asserts READ_PHONE_STATE is denied (it still asserts location and SMS are denied); this was an intentional test change, not a silent one.
- **Tests:** JVM 188/188; Redmi 87/87 (`SleepModeSettingsTest`, `SleepModeTest`); real journal hashes identical; release build and lint: 0 errors. Release APK SHA-256 a97871edf52ada51c1a9e87da85946120a112961485fa42b5d39bf6b453ddba5.
- **Not verified:** the Sleep screen's self-disable tapped by hand on the phone; other OEMs and carriers; Jio's behaviour for `*99#`.

## Advanced screen, cancel handling, setup-screen guard — 2026-11-11

- **Accessibility redirect:** Check Balance, Pay and the Home setup card open Android's Accessibility settings directly when the service is off. Profile > Advanced (was Payment Readiness) lists "Needs attention" with a fix button each, and "Recent problems" (`IssueLog`, memory only, generic text, 30 entries).
- **Stuck operation:** a Check Balance session whose carrier dialog was cancelled was never ended, so Pay said "Another cellular operation is active". The service now ends a balance session when no phone window remains (1.2 s before the PIN, 8 s after it) and shows the Balance unavailable page. A "Stop the stuck operation" button exists on the Pay screen and in Advanced. The 8 s wait after the PIN is a judgement; a very slow carrier reply could be reported as closed.
- **Setup-screen guard (`CarrierSetupPrompt`):** bank-choice, card-detail and PIN-creation screens stop assistance, type nothing, and explain (no IFSC needed; first 3 letters of the bank name, typed by the owner). Verified only with example wording in JVM tests; no real first-use screen was observed.
- **Other phones/banks/SIMs:** root-menu wording variants and extra system phone packages accepted. Unit-tested only; only the Redmi + Airtel/SBI is device-verified.
- **UPI PIN "not set" in PhonePe after a V4SH payment:** NOT reproduced and NOT explained. V4SH types only menu digit, UPI ID, amount, remark-skip; the PIN is typed by the owner. No fix claimed.
- **Tests:** JVM all pass (`CarrierSetupPromptTest` 5); Redmi 87/87 with Accessibility on (7 fail when it is off, a device-state dependency); real journal identical across that run. Release lint 0 errors expected; see build output below.

**Correction to the section above (2026-11-12):** release lint was NOT run for this change. Gradle could not download the lint tool (`lint-gradle:32.4.1`: TLS handshake to dl.google.com failed, and it is not in the offline cache), so the "0 errors expected" line is unverified. The release APK was built with the lint-vital tasks skipped (SHA-256 1f7441f60dc3603b8030f6d33044c361e8f868d351dc31de915462d09989bfaf); signing and 16 KB alignment were not rechecked. The previous lint result (0 errors) applies to the earlier build only.

## Bank setup helper — 2026-11-12

- **Feature (Profile > Advanced > Bank setup):** the owner can enter a bank name (saved, non-sensitive) and, optionally, debit-card last 6 digits and expiry (MMYY). V4SH types the first 3 letters of the bank when the carrier asks for them, and a card detail only when the screen asks for exactly that one in a stated format. Card details are memory-only (10 min maximum), never saved or logged, and erased after use, at session end, or on request. Each answer is typed once per session; a repeat of the prompt stops assistance. Never typed into a password-flagged field. OTP and UPI PIN are never typed (the owner does those).
- **Evidence:** JVM tests pass (`CarrierSetupPromptTest` now covers answers and memory-only expiry); Redmi 87/87 with Accessibility on; real journal identical across the run. NOT verified against any real first-time carrier screen (it could register a real SIM or set a real PIN); carrier wording is assumed.
- **Release:** built with lint-vital skipped (lint tool still not downloadable); SHA-256 027986e734ee4e7f517e880223abedc0b10d2c4621c4e7e161980954cdc70394. Lint, signature and 16 KB alignment not rechecked for this build.

## Phase 1 of the growth features — 2026-11-12

- **Added:** Pay again chips on Home (from carrier-confirmed successes only; `FrequentPayees`), Guided setup screen (Welcome > Check setup, Advanced), a 10-minute Home note after V4SH switches itself off, and Advanced > Report a problem (share sheet; device facts and problem TITLES only; `ProblemReport`).
- **Evidence:** JVM tests pass (`Phase1Test` 3); Redmi 87/87 after the owner re-enabled Accessibility and granted Phone permission. The Home chips, Guided setup screen and share sheet were not exercised on a real screen by me.
- **Incident (my install):** installing this build left the app freshly installed (first-install time = update time) with no payment-history database and runtime permissions reset (Phone, Phone state, Camera). `adb install -r` normally keeps data, so the cause is unknown (the phone's install prompt may have uninstalled first). The owner's earlier local V4SH history was lost; bank and carrier records are unaffected. The "journal identical" check could not be made for that run. The run after it showed one extra leftover seal file, which disappeared after the full suite; the database and the other seals were identical.
- **Note:** after any install the Accessibility service must be toggled off and on, and runtime permissions may need granting again.

## Phase 2 of the growth features — 2026-11-12

- **Added:** share-receipt image (carrier-confirmed successes only, via FileProvider cache, owner picks the target), Spending screen (monthly totals, top payees; carrier-confirmed successes only), unencrypted CSV export with formula defanging (owner picks the file; no import), optional app lock using the phone's own screen lock (`AppLock`, off by default; not applied when returning from a V4SH-started session).
- **Evidence:** JVM 202 pass (`Phase2Test` 4); Redmi 87/87 with the journal identical across the run; install kept data and permissions (first-install time unchanged). The new screens, the share sheet, the CSV save and the lock prompt were NOT exercised on a real screen by me.
- **Mistake caught:** the first version of the session-end timestamp broke a JVM test (`SystemClock` is not available there); fixed with a guard.
- **Limits:** CSV is readable by anyone who opens it; there is no encrypted backup/restore yet.

## Phase 3 of the growth features — 2026-11-12

- **Added:** scan a UPI QR from a picture (system picker, ML Kit, same routing as the camera), paste a copied UPI ID or `upi://` link on the Pay by UPI ID screen (one unambiguous ID only), one-tap balance from Home's card (starts at once only when Phone permission is already granted), and plain-language wording with a fix button for known failures on the balance failure page and the Pay screen (`ErrorHelp`).
- **Evidence:** JVM tests pass (`Phase3Test` 3); Redmi 87/87 with the journal identical; install kept data and permissions. None of the new screens were exercised on a real screen by me.
- **Behaviour change to note:** tapping Home's balance card can now dial *99# immediately.

## Phase 4 of the growth features — 2026-11-12

- **Added:** request-money QR with an optional amount (`ReceiveQr.payload(..., amount)`, invalid amounts ignored, round-trips through the app's own parser), a Demo walk-through that touches no carrier, Accessibility or journal, and a Language setting (English, Hindi, Telugu, or the phone's) applied through `attachBaseContext`.
- **Translation scope (deliberate):** only Home and the bottom tabs are translated. Payment, status and error screens stay English so a wrong translation cannot change what a money screen says. The Hindi and Telugu strings were written by me and NOT reviewed by a native speaker. Whether the app's custom font renders Devanagari and Telugu was NOT checked.
- **Evidence:** JVM 209 pass (`Phase4Test` 3); Redmi 87/87, journal identical; install kept data and permissions. The new screens were not exercised on a real screen by me.
- **Mistakes caught:** a newline landed inside a Kotlin string in my first Receive edit (compile error, fixed); the round-trip test first assumed the parser keeps "40" and it normalises amounts (test corrected, behaviour unchanged).

## Language coverage, Accessibility extras, support contact — 2026-11-12

- **Language:** a same-package `Text` wrapper (`TranslatedText.kt`) swaps ~250 fixed English sentences for Hindi or Telugu (`Translations`). About 80 long technical messages, text with changing parts (amounts, names) and error text stay English. Translations NOT reviewed by a native speaker; font rendering of Devanagari/Telugu NOT checked on the device.
- **Accessibility extras:** animated status light (`AwakeOrb`, with the 9-second auto-sleep countdown), Quick Settings tile (`V4SHTileService`, shows Awake/Asleep; tap sleeps, wakes or opens Settings), and an optional one-tap wake that needs the owner to grant `WRITE_SECURE_SETTINGS` once from a computer (`AccessibilityWake`; adds only V4SH's own entry; not run on the owner's phone).
- **Support contact:** Contact Support prepares a note (optionally with phone model/settings, no personal data) for SMS or WhatsApp to +91 96180 99307. V4SH has no internet permission, so delivery happens only when the user taps Send in their own app.
- **Evidence:** JVM tests pass (`Phase5Test`, `TranslationsTest`); Redmi 87/87 with the journal identical. The tile, orb, SMS/WhatsApp hand-off and translated screens were NOT exercised on a real screen by me.

## Setup-guard regression, app lock removal, auto-sleep default — 2026-11-12

- **Regression (my change):** the first-time-setup guard treated the normal Send Money method menu (which lists "IFSC and account number") as "choose your bank" and stopped every payment at the second step. Confirmed from the on-device log (`svc.setupPrompt BANK_SELECTION` right after the root menu). Fixed: "IFSC" alone no longer matches, a numbered payment-method menu is never a setup screen, and a bank-choice screen counts only as the first thing the carrier shows. Regression test added (`theSendMoneyMethodMenuIsNeverASetupScreen`). Not yet confirmed with a real payment by the owner.
- **Removed at the owner's request:** the optional app lock (feature, toggle, lock screen, tests). Auto-sleep now defaults to OFF, so V4SH no longer switches Accessibility off by itself; the switch remains in Sleep mode.
- **Evidence:** JVM tests pass; Redmi 87/87 with Accessibility bound. The real database file's hash was unchanged; one extra protected `.seal` file appeared during the run (the same kind of leftover seen in earlier runs, probably from a test database). I cannot prove its origin.
