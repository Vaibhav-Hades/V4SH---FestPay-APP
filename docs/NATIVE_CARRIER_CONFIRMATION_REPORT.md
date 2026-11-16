# Native carrier confirmation handoff

Date: 2026-11-05, Asia/Calcutta.

## Implementation

The existing payment reducer now stops at an exact carrier review matching the frozen VPA and INR amount. It never offers a confirmation input or clicks Confirm. The V4SH-side confirmation callback and button have been removed. QR/manual preparation, the existing engine, durable initiation, consent, connection-specific command ownership and unresolved-payment guard are retained. The real engine's launch and financial evidence policy are unchanged.

A later, fresh supported static instruction such as `Enter your UPI PIN` can move REVIEW to the operational PIN stage. This means a supported authentication interface was observed after matching review, not that PIN entry, authentication or payment completed. Manual confirmation is inferred from this progression; no confirmation click or entered PIN is captured. An arbitrary password field, generic PIN, OTP, incorrect-PIN message, changed/wrong recipient or amount review, unexpected authentication, stale event or wrong owner cannot authorize this handoff. Input/password nodes and their descendants are excluded from label collection; no PIN value is requested or modified. A password screen with no readable supported static instruction stops assistance conservatively.

The existing system-package, event-time/root-window, attempt, immutable command, consent, instance and connection checks still gate observations and writes. Input API success remains only a request; subsequent exact carrier review is required. The pre-PIN field writes and response-driven menu choices remain in the existing AccessibilityService. No second engine, request, permission, payment retry, reservation bypass, transaction-history change or analytics feature was introduced.

`CONFIRMATION_SENT`, the confirmation input enum and the serialized `authorized` field remain for compatibility with existing operational records. The new flow never offers a confirmation action or sets authorization. The service additionally refuses any confirmation input defensively.

## Dialog closure and return

An active-root change alone does not prove closure. The service now requests the public interactive-window accessibility flag and window-change events. This is a service configuration capability under the existing content-access consent, not a new Android permission. Only package/window metadata is read from other windows; their text is not collected.

The return path requires a removal event for the latest recognized owned carrier window, a complete nonempty current window list, absence of that window, no remaining approved phone window, an active root belonging to V4SH itself, and the same still-armed connection/command/reserved attempt. It checks again after a short debounce to reject transient replacements. The delay never proves closure, authentication, submission or settlement. Return is consumed once. Replacement windows, missing roots, incomplete lists, stale removals, lost consent/binding/ownership or storage uncertainty prevent automatic return.

After corroborated UI removal and V4SH becoming usable, the service stops operational assistance, releases only its in-memory observation lease, and requests the existing MainActivity task. This completes an Android/user return rather than forcing a foreground switch from Phone or Recents. The journey routes to the existing UNKNOWN outcome. No financial terminal state or durable reservation is released by closure. If Android denies the foreground request or does not expose sufficient closure evidence, the user must open V4SH after closing the carrier interface. V4SH does not open over an observed carrier window. This conservative path declines automatic foregrounding while Phone, Recents or another app is active, or while a normal Phone window remains; it must not be represented as universal USSD-session closure detection.

Public API basis: [AccessibilityServiceInfo interactive-window flag](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS) and [AccessibilityEvent window removal](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#WINDOWS_CHANGE_REMOVED). These describe UI windows, not carrier-network session completion. Reliable automatic return on this Redmi build remains a controlled-device-test requirement.

## Validation status

The complete JVM suite passes: **114 cases, zero failures/errors** (107 baseline cases plus seven focused cases). Baseline cases tied to the removed app-confirmation interaction are migrated to assert native-only confirmation; no baseline test case is deleted. All **47 distinct Android cases pass** (43 baseline plus four focused cases). The complete 46-case suite passed in 72.594 seconds; after adding the actual-screen navigation case, the entire four-case native-handoff class passed in 8.283 seconds. This is 47 distinct passing cases across those runs, not a claim of a single 47-case run. The protected real journal and seal matched their before-test SHA-256 fingerprints after the full run and again after the final focused run and Accessibility restoration. The existing UNKNOWN record/reservation remain byte-for-byte untouched.

Focused JVM coverage: exact/duplicate review without a confirmation action; wrong VPA and amount; stale authentication; password-flag/generic PIN/OTP rejection; result wording remaining UNKNOWN; lazy secret-reader exclusion; removal of only the current window; stale/wrong removals; incomplete/replacement/phone windows; lost ownership; one-shot return consumption; wrong attempt/session/command/connection. Existing source-policy tests retain stale root/event-window rejection.

Focused Android coverage: the actual CarrierPaymentJourneyScreen routes a synthetic closure to the UNKNOWN destination without accessing a journal/profile/transport or selecting SUCCESS; native review guidance with no app confirmation or text-entry control; production label collector excluding a synthetic password widget without modifying/clicking it; synthetic native review → PIN → result/closure leaving the durable UNKNOWN record and reservation intact across journal reopen. These are fixtures, not an actual carrier or automatic foreground Activity-return test. Existing Android recovery, UI evidence guards and connection/session transport cases remain in the complete run.

Closure removal flags are used on API 28 and later. Older supported Android versions, missing vendor events, incomplete window metadata and uncertain returns fall back to opening V4SH manually after closing the carrier dialog. No timer-based substitute is used.

## Build artifacts and evidence

- Final debug and signed/minified release builds passed, including release vital lint: `build/native-carrier-final-build.log` (3m 59s).
- Final JVM XML results: `app/build/test-results/testDebugUnitTest/TEST-*.xml`; 114 tests, zero failures/errors.
- Full Android run: `build/native-carrier-android.log`; OK (46 tests).
- Added navigation test build: `build/native-carrier-navigation-test-build.log`; PASS. Focused final Android run: `build/native-carrier-navigation-android.log`; OK (4 tests).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; release APK: `app/build/outputs/apk/release/app-release.apk`. Only debug/test APKs were installed on the Redmi, retaining existing application data.
- Fingerprints only, no transaction contents: `build/native-carrier-before.sha256`, `build/native-carrier-after.sha256`, `build/native-carrier-final.sha256`.
- Changed implementation: CarrierPaymentFlow, UssdNavigationPolicy, FestPayAccessibilityService, AccessibilityConnection, PaymentViewModel, CarrierPaymentJourneyScreen and the accessibility service configuration; small new CarrierDialogLifecycle window reducer. Existing engine launch code and financial journal implementation are unchanged by this task.
- Baseline reducer/UI test assertions were migrated to the requested native confirmation behavior. New tests: NativeCarrierConfirmationTest and NativeCarrierHandoffTest. Existing QR/manual preparation, history and analytics were not expanded.

Final safe device check: the user confirmed Payment Readiness shows enabled, connected and instance available. Android independently lists V4SH bound with window-state/content/window-change events and no crashed/binding service. Accessibility was restored after testing; no subsequent app reinstall, instrumentation run or forced restart was performed. No payment/balance operation was started.

## Physical limits

No real payment is authorized while the existing UNKNOWN reservation blocks it. No new payment, balance request, manual dial, carrier field submission or PIN entry is part of this implementation validation. The earlier feasibility report proves only initial-menu observation/action advertisement and modal blocking; it does not prove this payment handoff.

After legitimate reconciliation permits a controlled payment, verify actual VPA/amount acceptance; exact review recognition; user confirmation in the native carrier UI; supported static authentication instruction without PIN access; cancellation/window replacement; actual dialog removal events on Redmi/Airtel; and foreground return without false SUCCESS. Fixtures cannot prove carrier acceptance, bank settlement or physical payment completion. Authorized transaction-specific evidence remains unavailable, so the financial fallback stays UNKNOWN.

No commit or push.
