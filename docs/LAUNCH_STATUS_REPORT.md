# Operational launch tracking

Date: 2026-11-05. Existing UNKNOWN reservation remains protected. No real financial operation.

## Behaviour

PaymentResult now carries a separate nullable LaunchDisposition. PRE_LAUNCH_REJECTED means no Android launch was requested; LAUNCH_REQUEST_FAILED means the synchronous startActivity call threw; LAUNCH_REQUEST_RETURNED means that call returned, not that USSD activated. CARRIER_OBSERVED is published by the existing service only after the normal consent/connection/armed command/system-package/event-time/root-window gates and recognition of a supported payment prompt. Financial state remains UNKNOWN without authorized transaction-specific evidence.

The existing engine records SESSION_ARM_SUCCEEDED/FAILED, ANDROID_LAUNCH_REQUESTED, ANDROID_LAUNCH_RETURNED/THREW. The service records CARRIER_OBSERVED once. Events use the existing encrypted event table/transaction/seal architecture, deterministic attempt/session/milestone IDs and the existing record version. They verify the reserved attempt, immutable VPA/amount and PROCESSING/UNKNOWN state; they never change financial state or release a reservation. Duplicate milestone calls do not create duplicate events. No schema/encryption/key change.

ANDROID_LAUNCH_REQUESTED is a write-ahead dispatch-intent milestone immediately before the Android call. A crash between its commit and the call is inherently ambiguous; only RETURNED/THREW records describe that call's observed result. A missing post-call milestone does not prove the call was absent. Journal/Android side effects cannot be made one atomic transaction. Carrier observation also does not establish server acceptance or financial completion.

The coordinator's future PROCESSING reason is EXTERNAL_LAUNCH_PREPARING, not a premature arming claim. On engine return, its UNKNOWN transition reason reflects the launch disposition instead of always NO_AUTHORIZED_VERIFICATION_SOURCE. Existing historical records are never relabelled. Escaping exceptions still use PREPARATION_INTERRUPTED; storage uncertainty prevents further launch when detected before dispatch and retains the reservation. Post-dispatch evidence write failure is reported as such without retry or false failure/success.

Existing status UI uses the engine's actual message for preparation rejection, synchronous Android rejection, or returned request waiting for Airtel. The owned journey displays detected carrier interface only after supported observation progress. Native confirmation, manual PIN handling and financial terminal guards are unchanged. Operator-specific acceptance remains physically unproven.

## Files

- data/LaunchDisposition.kt: operational dispositions and milestone enum.
- ui/viewmodel/PaymentViewModel.kt: engine dispositions, milestone writes, coordinator reasons.
- data/PaymentAttemptJournal.kt: reserved-attempt-correlated append-only launch events.
- FestPayAccessibilityService.kt: first supported owned carrier observation and session disposition.
- ui/screens/CarrierPaymentJourneyScreen.kt: detected-carrier wording.
- androidTest/LaunchDispositionTest.kt: isolated engine branches and encrypted milestone assertions, deduplication, financial-state/reservation preservation and duplicate protection.

Paths are under app/src/main/java/com/festpay/app except the Android test under app/src/androidTest/java/com/festpay/app. Production PaymentViewModel was restored from recorded source/patches after a scripted edit truncated it; full validation is required before completion.

## Validation

Pending final runs and fingerprint comparison. Tests use unique synthetic stores and intercepted Android intents. Existing source/connection/reducer tests cover stale, wrong-owner and unsupported-window rejection; no live carrier request is permitted. No permissions, provider, second engine, history redesign, commit or push.
