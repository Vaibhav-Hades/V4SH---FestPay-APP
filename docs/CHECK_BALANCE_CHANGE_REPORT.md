# V4SH — Check Balance Change Report

Date: 2 November 2026

Historical scope note: this report describes the earlier balance-reset patch. Subsequent accessibility logging/input-read hardening and its newer test results are recorded in `IMPLEMENTATION_AUDIT.md`; statements below about remaining raw logging describe the earlier checkpoint, not the latest implementation.

Latest checkpoint: following the result-wait, session-activation, retained-input guard, and parser fixes described in `IMPLEMENTATION_AUDIT.md`, 49 tests and both builds passed. On 2 November 2026, the user confirmed that V4SH displayed the same balance as the carrier on the Redmi/Airtel test. This is one successful physical attempt, not production certification; earlier unverified/failed statuses below are historical.

## Scope

This report covers the latest Check Balance implementation change and its verification. It does not certify the app as production-ready. The earlier Phase 1 architecture documents describe target requirements; creating those documents did not implement their controls.

## Changes made

| File | Change |
| --- | --- |
| `app/src/main/java/com/festpay/app/FestPayAccessibilityService.kt` | Added `EXTRA_BALANCE_CHECK`. The session-reset receiver now reads the requested operation mode from the broadcast and applies it during reset. `UssdSessionData.reset(balanceCheck)` clears stale fields while setting the requested mode. Service connection preserves the current balance mode during initialization. |
| `app/src/main/java/com/festpay/app/ui/viewmodel/PaymentViewModel.kt` | Check Balance sends the balance-mode extra and initializes local session data using `reset(balanceCheck = true)`, including the null-context branch. |
| `app/src/test/java/com/festpay/app/CheckBalanceStep1LaunchTest.kt` | Added two regression tests: a repeated balance reset preserves balance mode while clearing stale payment/result data; a default payment reset clears prior balance mode. |

### Why this change was needed

Stage 1A already selected `3` when `isBalanceCheck` was true. Stage 1B already excluded balance sessions. Changing branch priority alone would not address the observed source defect.

Previously, Check Balance sent a reset broadcast and then set the shared balance flag. If the service handled the broadcast later, its reset cleared the flag to false. The root-menu code could then select payment option `1`.

The broadcast now carries the balance mode, so this specific delayed reset applies `true` instead of clearing the operation to payment mode. This is a source-supported cause addressed by the patch; it has not been established as the only cause of the reported physical `1 → 3` behavior.

## Verification completed

| Check | Result |
| --- | --- |
| Debug unit tests | PASS: 38 tests, zero failures/errors across three test classes. Includes the two new regression tests. |
| Debug APK build | PASS: `assembleDebug`. |
| Release APK build | PASS: `assembleRelease`. |
| Diff whitespace check | PASS. |
| ADB availability | ADB started, but no connected Android device was listed. |
| Physical Redmi/Airtel test | NOT PERFORMED. No end-to-end feature pass is claimed. |

Tests exercise the reset behavior, not a real broadcast/accessibility/carrier exchange. The suite also contains older tests that mirror implementation logic; a passing suite does not establish payment integrity or complete device compatibility.

Builds emitted deprecation/native-library stripping warnings but completed successfully. Build output includes generated artifacts/caches; no Git commit or push was performed.

## Operations we can expect now

### Check Balance — expected, awaiting physical validation

On a compatible device with the required phone permission, enabled accessibility service, eligible bank-registered SIM, and available USSD service:

1. The existing Check Balance action requests `*99#` using `ACTION_CALL`.
2. The balance launch and its reset broadcast both initialize balance mode as true.
3. When the existing service recognizes the initial menu, Stage 1A should choose `3`, fill the editable field, and attempt to click Send.
4. The payment-method branch should remain excluded while balance mode is true.
5. If the existing sensitive-screen detector recognizes the PIN screen, it stops navigation. The user must authenticate only in the real carrier screen.
6. The existing parser and UI attempt to extract and display the returned balance; a balance check does not create a payment transaction record through `startBalanceCheck()`.

Only steps concerning reset-mode preservation were directly changed. Launch behavior, menu recognition, Send actions, PIN detection, parsing, and UI display remain subject to device testing. The existing code marks input handled after setting text even if the Send click fails; successful submission is not guaranteed.

### Existing features — not newly verified by this patch

| Operation | What exists / current limit |
| --- | --- |
| Home, Transactions, Receive, Profile | Existing Compose navigation and screens; this change does not validate all interactions. |
| Scan QR and prepare payment | Existing CameraX/ML Kit scanning, parsing, recipient/amount/review flow. Validation weaknesses remain; no new scanning test was performed. |
| USSD payment initiation | Existing real engine normally opens `*99#` with `ACTION_DIAL` for payments. This is initiation, not confirmation. It still uses fixed delays to return AUTHENTICATION_REQUIRED. |
| Payment history | Existing SharedPreferences JSON history remains. It is not a bank statement or the durable attempt journal specified in Phase 1. |
| Receive | Existing UPI details/QR presentation remains; account ownership and end-to-end receipt of funds were not tested. |
| Pay by mobile number | Not established as a complete working end-to-end operation by this change or validation. |
| Carrier transaction-history import | Not established as working or production-ready. |
| Check Payment Status | Still a local delay/result replay; it does not query an authoritative bank/provider source. |

## Important limitations still present

- No established authorized, transaction-specific payment-confirmation mechanism. Production SUCCESS authorization remains blocked.
- Existing text classification can misclassify `unsuccessful` as SUCCESS, and success screens can make unverified completion/settlement claims. This patch does not fix those paths.
- Raw accessibility screen text is still logged before filtering. PIN protection remains heuristic; do not infer a guarantee that credentials cannot be exposed. Live authentication testing requires addressing this known logging defect first.
- Payment and balance operations still share mutable session data. This targeted mode-reset fix does not supply separate session identities, stale-event rejection, or a durable coordinator.
- Durable INITIATED attempts, crash-safe duplicate prevention, evidence-gated transitions, and recovery remain unimplemented target architecture.
- Mock execution remains in main source with runtime controls and shared history; release build success does not prove production isolation.
- Existing balance parsing can select unrelated numbers. A displayed balance is not proof that a specific payment succeeded.
- Existing backup, release-hardening, ViewModel ownership, and history-selection defects remain outside this patch.

## Next validation checkpoint

Connect the Redmi Note 13 Pro+ with USB debugging enabled. After addressing sensitive logging before any authentication test, validate the supported configuration in checkpoints:

1. Tap Check Balance and verify automatic USSD launch.
2. Verify the root menu receives only `3`, never `1 → 3`.
3. Verify Send is actually activated and duplicate accessibility events do not repeat submission.
4. Verify automation stops at authentication; never record, share, or automate the PIN.
5. Separately verify the non-sensitive balance response and correct display without creating a payment transaction.

Until those checkpoints are observed, the accurate status is: **code fix implemented; unit tests and both builds passed; physical Check Balance operation unverified.**
