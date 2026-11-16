# Core payment functionality — current implementation

3 November 2026. Existing V4SH at D:\FestPay; no new project, unrelated screen redesign, new payment method, provider credentials, real payment, guard release, commit or push.

## What changed

| Area | Implementation / files |
| --- | --- |
| Shared recipient | New data/PaymentRecipient.kt defines immutable PaymentRecipient with VERIFIED, UNAVAILABLE, INVALID_UPI_ID, PENDING and ERROR, separate provenance/time and typed problems. QR and manual flows use the same PaymentPreparation/BeneficiaryNameResolver instance through SharedPaymentState. |
| Real lookup boundary | AuthorizedBeneficiaryProvider defines an adapter trust boundary, with no production implementation, endpoint, credential, runtime toggle or network permission. The production resolver immediately reports UNAVAILABLE for valid syntax. Synthetic adapters exist only in src/test. This is not a fake live integration. |
| Response acceptance | Require exact lookup ID and VPA, non-empty bank-record name, matching configured source and bounded non-future freshness. Reject malformed/missing provenance, old/wrong-recipient responses and provider errors; timeout means ERROR, never financial failure. Generation tickets reject old replies even when a VPA is reused. The real adapter would first have to authenticate its bank/provider evidence; metadata alone is not proof of bank authority. |
| QR | UPI QR parsing preserves the VPA and validated amount/currency/note. Requester-controlled pn is not copied to the shared recipient or displayed as bank identity. A new/invalid scan clears the prior recipient/amount/note/verification. Explicit zero/negative amount is rejected per this request; missing/blank amount requires positive user entry. |
| Manual VPA | data/PaymentInputValidator.kt normalizes only outer whitespace, preserves address case/content and rejects controls/malformed/empty/long input. MobilePaymentScreen.kt clears old recipient data on entry/edit, provides Check beneficiary name, displays the address and explicit status, and permits correction before Review. Syntax does not establish account existence. |
| Frozen Review | ScreenPlaceholders.kt snapshots recipient, canonical amount, note and draft revision when entering Review. The rendered snapshot and Pay arguments use the same object. A recipient/amount/note/verification revision change disables Pay and requires a fresh Review. Per-selection amount state prevents a prior scan's editor contents carrying over. Bank-returned names have no editable field. |
| Unsupported note transport | Existing *99# engine has no qualified remarks/note submission. Preparation labels notes local/not sent; no claim that the carrier receives them. |
| Payment execution | Both routes still reach the same ReviewPaymentScreen, existing shared PaymentViewModel, RealUssdPaymentEngine, PaymentIntegrityPolicy and encrypted PaymentAttemptJournal. Unverified QR names are not passed as journal bank names. No financial policy, USSD navigation/PIN code, journal schema or reservation behavior was changed. |

Main paths are relative to app/src/main/java/com/festpay/app. The only application changes are PaymentRecipient.kt, PaymentInputValidator.kt, UpiQrParser.kt, MobilePaymentScreen.kt and payment-specific sections of ScreenPlaceholders.kt. Help/Profile/Receive/Support, themes and release/security configuration were not changed.

## Recipient identification

| Requested result | Current evidence/status |
| --- | --- |
| QR VPA extraction | PASS — strict parser, stale-scan tests and fresh user-confirmed offline preparation/Review. |
| Manual UPI ID validation | PASS — syntax/normalization tests and fresh user-confirmed ID/amount Review. Not account-existence verification. |
| Authorized beneficiary-name API | Official Validate Address capability FOUND/documented; authorized, usable V4SH access NOT FOUND. No sponsor/provider relationship or current API schema/credentials supplied. |
| Carrier beneficiary-name response | NOT TESTED — existing UNKNOWN reservation does not legitimately permit the requested carrier navigation. No manual/out-of-app bypass. |
| Verified name displayed correctly | BLOCKED as a real capability. Synthetic acceptance/UI exclusion tests do not establish a live bank name. Actual UI truthfully shows UNAVAILABLE and “Beneficiary name could not be verified.” |

BENEFICIARY_VERIFICATION_INVESTIGATION.md answers all eight access/schema/provenance questions and links primary sources. NPCI's documented name rule is why QR pn is no longer used as the displayed beneficiary name. This implementation is not a declaration of institutional compliance.

## QR field behavior

| Parameter | Current behavior |
| --- | --- |
| pa | Required valid payment address; percent decoded once, address case preserved; not proof of existence or ownership. |
| pn | Validated as bounded untrusted metadata during parsing, then excluded from the displayed/shared recipient. No fallback, username derivation or contact-name lookup. |
| am | Positive exact INR amount with at most two decimals; absent/blank allows manual amount entry; explicit zero/negative, excess precision and malformed values rejected. This intentionally supersedes the prior zero-as-unspecified rule; its existing test was updated to the newly requested stricter behavior. |
| cu | Absent/blank defaults to INR; non-INR rejected. |
| tn | Bounded local preparation note; not sent by the current USSD adapter and not falsely presented as a bank remark. |
| Additional metadata | Unknown fields are not interpreted as bank identity, signature, provider transaction reference or merchant-order binding. mc/mode/orgid/tr/tid/sign/url are not validated/transmitted as a provider contract. This is simple VPA preparation, not signed merchant-request/order certification. |

Malformed URI/action, duplicate case-insensitive fields, invalid VPA, control characters and excessive lengths remain rejected. Repeat/new scan overwrites old data; invalid scan leaves no ready request. Own Receive QR generation was not modified and omits amount.

## Payment initiation

| Requested result | Status / scope |
| --- | --- |
| QR preparation navigation to Review | PASS — fresh physical confirmation; no Pay. |
| Manual preparation navigation to Review | PASS — fresh physical confirmation; Check beneficiary name reports unavailable without making up identity. |
| QR payment navigation into carrier | BLOCKED by the existing real unresolved reservation. |
| Manual UPI ID navigation into carrier | BLOCKED by the same guard/engine. |
| Recipient entry in carrier | BLOCKED for new physical testing; source remains the existing owned ordered VPA path. |
| Amount entry in carrier | BLOCKED for new physical testing; canonical frozen amount supplied to the existing engine only if legitimate preflight permits. |
| Carrier Review | BLOCKED for new physical testing. No current new-result PASS claimed. |
| Manual PIN boundary | PASS for unchanged source/automated boundary checks and earlier physical balance evidence. No new payment PIN screen/test was opened in this run. |

The actual real engine awaits the owned-session ACK before ACTION_CALL, passes its immutable local attempt UUID/recipient/amount, and returns UNKNOWN. Existing journal persists INITIATED/event/reservation before external interaction and central policy forbids unsupported terminal authority. These are preserved source/test findings, not fresh proof of a successful live transfer.

## Payment result

Confirmation mechanism: NOT FOUND for current integration.  
Attempt correlation: UNAVAILABLE.  
SUCCESS authorization: BLOCKED.  
FAILED authorization: BLOCKED.  
Existing unresolved attempt: PROTECTED.

Beneficiary verification is separate from financial finality: even a synthetic VERIFIED recipient cannot change PaymentIntegrityPolicy or settle an attempt. AUTHORIZED_CONFIRMATION_CONTRACT.md remains the required financial evidence/authorization contract. No new adapter, SMS/carrier/balance inference, fake transaction reference, automatic retry or reservation release was added. The user explicitly confirmed Pay remains blocked.

## Tests and physical evidence

- Before changes: current JVM/build baseline 74 PASS. Prior complete 30-Android PASS is preserved in the historical reports; the attempted unchanged Android rerun stalled and was stopped, so that rerun is not counted as PASS.
- Final: **82/82 JVM and 31/31 Android tests PASS**. All prior test coverage retained, with old QR-name/zero-amount expectations updated to the explicitly requested semantics. No tests removed or suppressed.
- New BeneficiaryVerificationTest covers valid/malformed/missing/whitespace/case/long/empty input, configured-source acceptance with TEST_ONLY evidence, unavailable/invalid/provider error/timeout, missing/malformed provenance, old/wrong-VPA/wrong-request replies, concurrent selection changes, Review invalidation and expiration. No provider calls or real names.
- New Android QR-name/frozen-Review case checks pn never appears as identity, correct unavailable status, unchanged displayed snapshot and disabled Pay after a new recipient. Existing manual flow and stale-QR tests now exercise the shared model.
- The first updated UI run also stalled in an existing lifecycle fixture and was stopped. With the phone unlocked/readied and actual Accessibility temporarily off, the subsequent complete 31-test suite passed. This does not establish the cause of the stalls; no timeout or financial protection was weakened. Service was restored for the user's preparation checks. No further test restarts planned.
- Debug, test APK and signed release builds PASS; unchanged release lint gate PASS: 0 errors, 41 warnings, 2 hints. Test-only beneficiary/payment fixtures absent from release DEX. Release runtime remains unqualified as previously documented; no destructive replacement of the debug installation was attempted.
- Current signed release SHA-256: 76C1AFBA84ACECB9C8222259F26718DD6F6A9E96A828D008B01FEE034F8CEE71.
- Fresh user confirmation: offline QR VPA/amount Review correct with unavailable-name status; manual Check beneficiary name and amount/Review correct, no invented name and Pay remains blocked. Accessibility restored. No new Check Balance/Help/Profile/Receive/Support or carrier payment was performed merely to repeat completed work.

## Final status

QR and manual UPI ID preparation/recipient-state handling are implemented and tested. Verified bank beneficiary resolution and live payment confirmation require real external authorization/infrastructure. Carrier recipient-name observation remains NOT TESTED because the guard cannot be bypassed. NOT PRODUCTION READY; no live payment completion claim.


## Final device-metadata qualification

After the user's successful preparation checks, a final read showed V4SH in Enabled Accessibility services but not in Bound services; mobile data was OFF and Wi-Fi was ON at that final snapshot. The user had reported restoring Accessibility and running the preparation checks offline. The snapshot is not a simultaneous measurement of the earlier user actions, so those are recorded as user-reported offline checks, not a new timed connectivity certification. Automated offline decoding/no-INTERNET coverage remains PASS.

Do not claim current carrier readiness from an enabled settings switch. The service must actually be bound before any otherwise-legitimate USSD operation, and the real unresolved reservation independently continues to block a new payment. No additional app restart, service bypass or carrier launch was performed. QR/manual preparation remains independent of Accessibility binding. The cause of the final disconnect was not established in this focused task; the previously documented stop/rebind limitation remains relevant.


## Phase 1 readiness and lifecycle follow-up — 2026-11-03

Current implementation and evidence: [PAYMENT_READINESS_PHASE1_REPORT.md](PAYMENT_READINESS_PHASE1_REPORT.md). LOW_MEMORY process death and enabled/unbound service state were directly observed; the OEM rebind cause remains unproven. Connection-generation ownership, inert ACK-before-attempt preparation, durable intent-before-arm, diagnostics and manual recovery were added. UNKNOWN remains protected with no provider reference or authorized reconciliation source. No real payment was launched. The final 88 JVM tests and 36 Android tests passed. Physical Redmi readiness/recovery/background/resume/force-stop checks now PASS, supported by direct Android binding, safe diagnostics, user UI confirmation and unchanged journal/seal hashes. The service was left connected. This completes the requested service/readiness validation for the tested build/device; financial reconciliation and payment SUCCESS/FAILED authorization remain BLOCKED by absent authorized provider evidence. The historical OEM rebind cause is not fully established; no automatic recovery guarantee or production approval is claimed.


## Core journey extension — 2026-11-04

See [CORE_PAYMENT_JOURNEY_REPORT.md](CORE_PAYMENT_JOURNEY_REPORT.md) for the current iteration: guarded response-driven payment progression, explicit carrier amount/review authorization, structured unverified carrier details and encrypted operational observations. Final results: 105 JVM and 42 Android tests PASS, debug/test/signed release builds and release lint PASS, fresh QR/manual preparation PASS. Earlier UI test failures and the later uninterrupted full-suite pass are preserved in that report. The protected UNKNOWN journal/seal are byte-for-byte unchanged. No new real carrier payment was allowed; complete live amount/confirmation/PIN/settlement verification remains BLOCKED.
