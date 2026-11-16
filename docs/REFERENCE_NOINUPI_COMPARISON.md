# Reference study: NoInUPI (ZIP supplied 2026-11-06)

Source studied (read-only, extracted outside the project; none of its code was run or copied):
`NoInUPI-main/app/src/main/java/com/noinupi/{domain,platform,presentation}` plus manifest and accessibility XML.

## Licence

The ZIP contains **no LICENSE, COPYING or NOTICE file**, so no reuse right is granted. Nothing was copied. The behaviours below were reimplemented independently in V4SH's own style and wording. Its README also tells users to disable Play Protect; V4SH does not adopt that.

## What the reference actually does

Pay → `Actions.SendUpi` (`domain/Actions.kt`) dials the shortcut `*99*1*3#`, then `ActionRunner.runAction` (`domain/ActionRunner.kt`) walks frames from `UssdEngine` / `UssdAccessibilityService` (`platform/`): UPI ID → amount → remark (replies `1`) → PIN (no reply; user types it) → "confirm" (replies `1`) → success. `MainActivity.startPayment` (presentation) saves a `Transaction` to plain SharedPreferences (`domain/TransactionStore.kt`) when the runner emits `Done` ("Success") or `Error` ("Failed").

## Comparison

| Functionality | Reference | V4SH (before this pass) | Action |
|---|---|---|---|
| Payment initiation | `ACTION_CALL` on a `*99*1*3#` shortcut; 2 s double-tap cooldown | `ACTION_CALL` on `*99#` after durable attempt + reservation + ACK handshake | **Keep V4SH.** The shortcut hard-codes menu digits and is unverified on Airtel; recorded as an option needing an authorized physical test |
| USSD execution / session | Instance singleton service; session counter; 30 s engine timeout; dismisses any leftover dialog before dialling | Owned command/session/epoch, armed only after ACK, never touches an old dialog; 120 s / 300 s (PIN) timeouts | Keep V4SH (safer) |
| Menu navigation | Regex steps over `*99*1*3#` frames | Digits derived from uniquely observed labels | Keep V4SH |
| UPI ID / amount entry | `{vpa}`, `{amount}` templates, 250 ms pacing delay | Frozen reviewed values; entry only on a fresh editable prompt; acceptance only from a later prompt | Keep V4SH (no delay-as-proof) |
| Remark handling | Any "remark/comment/note" text → reply `1` | Reply only to a unique explicit "N to skip" instruction | Keep V4SH |
| PIN handling | Step with no reply; user types in carrier dialog | Password flag / sensitive wording stops automation; PIN prompt payment details compared to the immutable request | Keep V4SH |
| Result detection | Text patterns (`successful`, `ref … \d{6,}`) → **SUCCESS**; any Error/terminal frame → **Failed** | Wording is an observation only; financial state stays UNKNOWN | **Rejected** — conflicts with the evidence rule |
| Transaction reference | Regex "ref no" number inferred from text | Never inferred; none stored | Rejected |
| Transaction logging | Plain SharedPreferences JSON list, saved only at the end, no attempt id, duplicates possible, lost if the app dies mid-payment | Encrypted Keystore-backed journal; durable INITIATED before launch; idempotent events; restart recovery | Keep V4SH (reference is weaker) |
| Failure handling | Large regex list → "Transaction failed" | Unsupported/unknown prompt → safe stop | **Adopted idea**: carrier-rejection wording → precise stop reason |
| Unknown status | None (everything is Success or Failed) | UNKNOWN is the default | Keep V4SH |
| Filler frames | `FrameFilter` ignores "processing/loading/connecting…" | Only "please wait / ussd code running" | **Adopted idea** |
| Submit control | Clicks any button labelled send/ok/submit/reply, else clicks the field | Only an exact "Send" | **Adopted idea** (exact labels only) |
| Event scoping | `android:packageNames` limited to phone/telecom/dialer packages incl. Samsung telephonyui | Source policy in code; no manifest filter | Not changed (see open items) |

## Implemented from the study (independent code, no UI change)

| Change | Files | Why |
|---|---|---|
| Transient filler frames ("Processing…", "Loading", "Connecting…") are ignored, narrowly (non-editable, short, starts with the word) | `data/UssdNavigationPolicy.kt` (`isTransientPlaceholder`), `data/CarrierPaymentFlow.kt` | A benign filler frame used to stop assistance as "unsupported" |
| Pre-authentication carrier rejection (invalid UPI/option, unknown bank, limit, insufficient funds, service down, session expired, self-pay …) stops assistance with `CarrierIssue.CARRIER_REJECTED`, even when the frame re-shows a known prompt | `data/UssdNavigationPolicy.kt` (`isCarrierRejection`), `data/CarrierPaymentFlow.kt` | Previously "Invalid UPI ID — Enter UPI ID" looked like a known prompt and V4SH waited silently until the 120 s timeout |
| Submit control accepts exactly Send, Reply, Submit or OK (Send preferred), never substrings | `data/UssdControlPolicy.kt`, `FestPayAccessibilityService.findSendButton` | Dialogs that label the button differently could not be submitted |

User-visible effect: the existing "Assistance stopped: …" sentence now reads "carrier rejected" for that case. No screen, layout, colour or navigation was changed. The financial state is unchanged: a rejection is an observation, the attempt stays UNKNOWN and its reservation is untouched.

## Not adopted, deliberately

- Declaring SUCCESS/FAILED from carrier text, inferring a transaction reference, auto-confirming with `1`, deleting/dismissing an old dialog before dialling, delay-based pacing as proof, plaintext persistence.
- The `*99*1*3#` shortcut and the Samsung package name: unverified for this carrier/device; adding either to a financial automation without a live test would break V4SH's "only verified prompts" rule.

## Owner decision (2026-11-06): keep the reservation locked

A carrier rejection **before the PIN prompt** leaves the attempt UNKNOWN with its reservation active, which blocks every later payment. `PaymentIntegrityPolicy.allows` permits `CANCELLED` (which releases the reservation) only from INITIATED, i.e. before launch. The owner was offered a narrow release rule (no PIN prompt observed, owned continuous session, explicit rejection wording) and **declined it**: no rejection wording, however explicit, releases a reservation. The policy is unchanged. Releasing a reservation still requires authoritative reconciliation. The consequence the owner accepted: a mistyped UPI ID that the carrier rejects will keep payments paused until the attempt is reconciled.

## Test mapping (fixtures only; no live payment was made)

| Requested scenario | Where covered |
|---|---|
| Valid/invalid UPI ID, valid/invalid QR, missing/invalid amount | `PaymentInputTest`, `QrJourneyInputTest`, `QrRejectionWordingTest`, `UiPresentationTest`, `QrRejectionRoutingTest` (device) |
| Expected / unexpected menu, UPI ID prompt, amount prompt | `CarrierPaymentFlowTest`, `ReferenceInformedBehaviorTest` |
| Optional remark and explicit skip | `StandardUssdFlowTest` |
| Unexpected PIN; PIN with matching / mismatching / incomplete details | `StandardUssdFlowTest`, `NativeCarrierConfirmationTest` |
| Timeout, interruption, duplicate/stale callbacks, UNKNOWN | `StandardUssdFlowTest.timeoutAtPin…`, `CarrierPaymentFlowTest` |
| Authoritative success / failure | Not testable: no authoritative source exists (`PaymentIntegrityPolicy.allows` rejects SUCCESS/FAILED) |
| Record creation, persistence after restart, duplicates, preserved records | `PaymentJournalTest`, `ActivityRecoveryTest`, `CarrierObservationJournalTest`, `PaymentHistoryTest` (device) |
