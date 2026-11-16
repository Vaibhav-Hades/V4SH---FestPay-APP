# Redmi/Airtel USSD interaction feasibility

Date: 2026-11-04, Asia/Calcutta.

**Decision: PARTIALLY SUPPORTED.** The actual V4SH AccessibilityService could observe the real initial Airtel banking menu, identify its window and inspect its advertised input actions. However, the carrier dialog remained on top and blocked V4SH touches after the user's Recents/quick-switch attempt. V4SH became usable after cancellation. The intended usable-V4SH → same-carrier-session round trip was **not demonstrated**. Native field execution and network-session continuity were deliberately not tested by submitting anything.

This is a physical observation report, not payment approval or proof of settlement. No payment, balance operation, recipient, amount, PIN, OTP or carrier menu choice was submitted. No second USSD request was issued while a session was active.

## A. Device observations

| Item | Direct observation |
|---|---|
| Device | Redmi Note 13 Pro+; adb model 23090RA98I, product zircon_in |
| Android | 16, API 36 |
| Redmi software | HyperOS 3.0; incremental OS3.0.301.0.WNOINXM |
| Operator label | `airtel,` from gsm.operator.alpha; no phone/account identifiers collected |
| Binding before/after diagnostic | Android listed V4SH enabled and bound, with no crashed/binding entry |
| Service throughout recorded samples | Connected, actual instance available; same PID 14047 and connection generation |
| App operation throughout samples | session_armed=false and operation_active=false; no V4SH financial session was claimed |
| Service configuration | canRetrieveWindowContent=true; observed service_flags=0 and interactive_window_count=0 |

The service was temporarily disabled by the user for isolated regression tests, then restored before the physical menu test. It was left bound and the normal V4SH MainActivity was restored afterward.

## Existing implementation and exact restriction

The inspected production path is unchanged:

1. PaymentViewModel validates the immutable Review, consent, phone/cellular prerequisites, actual binding and the existing reservation. An unresolved ID blocks a new payment before carrier launch.
2. An allowed payment would initialize an owned, connection-specific acknowledged command, durably create INITIATED/reservation, transition through the existing coordinator, and call RealUssdPaymentEngine.
3. The real engine checks the exact durable record/command, arms the existing service and invokes public `Intent.ACTION_CALL` with encoded `tel:*99#`. No new initiation API or engine was introduced.
4. Accessibility observation uses `rootInActiveWindow`, password/static-label filtering and window/package/event-time checks. Payment events require the current armed session and matching request. The service intentionally ignores an independently opened, unowned menu for automation.
5. Supported root/method/VPA/amount inputs use public ACTION_SET_TEXT plus a Send click, with response-driven progression. Input API success is not input acceptance; a subsequent exact review is required. Explicit confirmation and manual PIN boundaries remain intact.

Relevant source locations: PaymentViewModel.kt:63/74/230/264, FestPayAccessibilityService.kt:234/237, AccessibilityConnection.kt, CarrierPaymentFlow.kt, UssdNavigationPolicy.kt and CarrierPaymentJourneyScreen.kt.

There is no safe initial-menu-only production command. The payment command is blocked by the protected UNKNOWN record. Using Check Balance would automatically select an operation and violate this diagnostic's scope. Neither path was used or altered.

**Safe independent method used:** after explicitly starting a passive debug-only observer, the user manually dialled *99# once through Phone. This did not invoke the guarded payment engine, create a V4SH financial attempt, arm the service, or release a reservation. The observer called the actual existing service's public root/window metadata APIs without performing any carrier action. It was not a fake carrier dialog or UiAutomation substitute for that service.

Important limit: this was a Phone-initiated initial menu, not a fresh launch through the guarded V4SH payment route. Any launch-attribution differences between those paths remain untested; the guard was not bypassed to compare them.

## B. Actual USSD behaviour

**What opened:** Android foreground metadata identified `com.android.phone/com.android.phone.MiuiMMIDialogActivity`. The actual service read an `android.widget.FrameLayout` root belonging to `com.android.phone`, recognized the initial banking menu from supported labels, and observed native input/Send controls. It was a system-phone MMI interface, not a V4SH screen or synthetic response.

Metadata-only timeline, seconds relative to the user's probe Start tap:

| Time | Active root | Observation |
|---|---|---|
| +0.106 s | V4SH, window 1861 | Probe screen; service connected; unarmed |
| +23.820 s | OTHER, window 2 | Unrelated window; its contents/package were not retained |
| +95.842 s | com.android.phone, window 1872 | Actual initial menu; editable field/Send found; retained field refresh=true |
| +160.232 s | com.android.phone, window 1873 | Different phone window, not recognized as initial menu; previous field refresh=false |
| +163.084 s | com.android.phone, window 1872 | Same original menu window observed again; original field refresh=true |
| +164.289 s | V4SH, window 1861 | Own interface active again; previous carrier field refresh=false |
| +180.207 s | Probe deadline | Read-only observation stopped automatically |

These are change-triggered snapshots, not network latency measurements or the timestamps of native AccessibilityEvents. No timing proves authentication, submission or backend liveness. Window 1873's purpose was not captured and is not inferred.

**User's physical account:** the user tried selecting V4SH through Recents/left-right quick switching. V4SH appeared behind the carrier dialog, but its background touches did not work. The user confirmed the dialog remained visibly on top, and that V4SH responded normally after Cancel. This supports modal/focus blocking in this trial rather than a persistent V4SH freeze. The active accessibility root remained the phone window until the later V4SH root observation.

**Retention:** the same original Android menu window 1872 and refreshable field reappeared after the intermediate phone window. That establishes UI-window re-observation, not the identity or continued validity of a carrier-network USSD session. No usable V4SH interval followed by a verified server reply was established. No second request was used to replace or recreate the menu.

**Staleness:** the old field's refresh result changed true → false → true → false as windows changed. A cached field must never be assumed usable across transitions. The diagnostic retained it only to call public refresh/read metadata; it never acted on it.

**Lifecycle/event limits:** the PID and service generation stayed constant in recorded samples. Mid-session process recreation was not forced/tested. Original native event ordering was not logged; production rejects unowned events by design. The short foreground-component trace covers menu appearance, not every later Recents movement. The later transition conclusion uses the actual service snapshots plus the user's explicit observation, not an invented complete focus trace.

## C. Input capability

| Capability | Physical carrier evidence | Conclusion |
|---|---|---|
| Editable control | android.widget.EditText, editable=true, enabled=true, password=false, window 1872 | Observed |
| ACTION_SET_TEXT | Advertised action ID 2097152 on the actual field | Available in its accessibility action list; execution NOT TESTED |
| Send | Actual Send control found; click action advertised | Available in its action list; click NOT PERFORMED |
| Field identity | Window ID captured; resource ID null under current service configuration | Do not infer that the platform has no resource ID |
| Carrier accepts programmatic text | No text entered or field value read | NOT TESTED |
| Carrier accepts Send / changed next prompt | No menu choice or Send submitted | NOT TESTED |
| Old field stale/refreshable | Refresh results above | Observed; not authorization to reuse a cached node |
| New USSD request replacing an old session | No second request issued | NOT TESTED; no replacement strategy is recommended |

The controlled Android fixture used visible, attached local EditText/Button widgets with known nonfinancial text. Public widget ACTION_SET_TEXT and click handlers accepted the fixture input/click. This is **not** a remote carrier-node action test, server acceptance, a simulated carrier PASS or evidence that the same USSD session advances.

The first fixture run's detached button did not advertise a click and its assertion failed. The fixture was corrected to attach the same controls to a real window and wait for layout; all action/result assertions were retained. The final full run passed **43/43 Android tests** in 63.329 s, retaining all prior 42 cases plus this fixture. The **107/107 JVM baseline** passed. Debug/test builds passed. No production source, settlement policy, transaction history or existing tests were modified for this investigation.

## D. Architecture decision

**PARTIALLY SUPPORTED — scoped to this Redmi/HyperOS/Airtel initial-menu trial.**

- Supported and physically observed: actual service binding; reading the real system-phone initial menu; identifying its window/package and advertised controls; re-observing the original window without another dial; stale-reference detection; normal V4SH use after Cancel.
- Not supported as a usable UX in the observed attempt: interacting with V4SH behind the active modal MMI dialog. The requested in-app confirmation handoff while that dialog remains in front did not work.
- Not established: native input execution, changed carrier response after Send, a live backend session surviving an interactive V4SH round trip, full owned-payment event handling, payment authentication or finality. No financial input was allowed merely to fill those evidence gaps.

The active-window accessor follows the user-touch/input-focus window. The separate interactive-window list requires an opt-in flag and is currently empty. Those API semantics are documented in the [Android AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getRootInActiveWindow()). Opting into another window-list flag would not itself make a modal-covered V4SH screen touchable; no flags or permissions were changed to try to force that result. The physical conclusion comes from this device's metadata/user observation, not from that documentation.

This is not a universal claim that all Redmi/Android versions reject app switching, nor that foreground USSD automation is impossible. It is a negative result for the current attempted usable-V4SH mid-dialog handoff, with positive observation/action-advertisement evidence and explicit untested areas.

## E. Recommended next implementation

**Smallest supported direction to evaluate next: keep the carrier interface foreground from launch through native confirmation and manual PIN.** Retain V4SH's existing QR/manual preparation, frozen amount, owned response-driven menu/VPA/amount assistance, then let the user explicitly confirm on the actual carrier confirmation screen and enter PIN there. Return to V4SH's honest result experience after the dialog ends. Do not require a V4SH button behind the modal phone interface.

The narrow future code change is a manual-native-confirmation handoff in the existing CarrierPaymentFlow/AccessibilityService. Today REVIEW expects app authorization before CONFIRMATION_SENT and otherwise rejects an early PIN screen. A native-confirmation path would need to stop automated confirmation at an exact matching review and recognize the subsequent observed manual-authentication handoff without entering or reading PIN. Preserve the same session/connection, fresh-window/label checks, explicit user confirmation, UNKNOWN guard and financial policy. Do not simply auto-press Confirm or accept an arbitrary PIN screen.

This recommendation was **not implemented** here: the task requested feasibility evidence, not another feature. An authorized controlled test is still needed before claiming native input/confirmation acceptance. The protected UNKNOWN attempt must first be legitimately resolved before any new real payment; no manual acknowledgment or diagnostic can clear it. No overlay over authentication, private API, root, reflection bypass, new engine, extra permission or architecture redesign is recommended.

## Evidence, changes and preservation

- `build/ussd-feasibility-window-snapshots.jsonl`: metadata queried through the actual service; no raw screen text, field values, recipient, amount, PIN or OTP stored.
- `build/ussd-feasibility-focus.jsonl`: allowlisted package/activity component changes; other components redacted.
- `build/ussd-feasibility-android-final.log`: final full 43-test run. Initial failed fixture run remains in `build/ussd-feasibility-android.log`; it is not counted as PASS.
- `build/ussd-feasibility-jvm-final.log`: 107 JVM tests; `build/ussd-feasibility-final-build.log`: final debug/test build.
- Before/after protected journal and seal SHA-256 values match. UNKNOWN and its reservation were not cleared, relabelled or modified.
- Changes: debug-only UssdWindowProbeActivity, debug-only UssdControlFixtureActivity, their debug manifest entries, one Android fixture test and this report. Production payment code, service flags, core UI, journal/history and settlement rules were untouched. The probe never calls a dialer, claims/arms a command or performs carrier actions; the user opened Phone independently.
- Probe stopped at its deadline; no observer remains running. Service left enabled/bound, normal V4SH screen restored. No real payment, financial menu selection, bank data entry, PIN/OTP action, commit or push.

No physical carrier PASS is inferred from the fixture tests or action advertisement.
