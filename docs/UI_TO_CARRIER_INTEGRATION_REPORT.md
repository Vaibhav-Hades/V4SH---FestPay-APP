# UI-to-carrier integration

Date: 2026-11-05. No real payment or balance operation is permitted in this validation.

## Connected buttons and production path

Home Scan QR → qr_scanner → shared PaymentPreparation → amount_entry → ReviewPaymentScreen. Home Pay by UPI ID and scanner manual-entry fallback → upi_payment → the same preparation/amount/Review. Review's Pay button calls PaymentViewModel.startPayment with the frozen recipient VPA, canonical amount and current-snapshot predicate. The coordinator acknowledges inert session preparation, creates durable INITIATED/reservation, transitions PROCESSING, then invokes the single RealUssdPaymentEngine. That engine validates the same reserved record/command, arms once and requests ACTION_CALL with tel:*99#. No mock engine is used.

This connection already existed following the previous launch-order fix. This task adds an executable isolated proof of the actual Review button through the real coordinator/engine, rather than adding another payment implementation. The engine now receives the same journal factory as its coordinator; production defaults remain the same protected journal. This also prevents isolated coordinator tests from accidentally checking the real production journal.

## Observed progress and UI

- Review displays preparing carrier launch during the actual pending Pay action.
- WAITING explicitly says launch requested and waiting for an actual supported menu.
- METHOD and RECIPIENT describe requested menu selections and waiting for subsequent prompts, not accepted input.
- AMOUNT_PENDING describes VPA submission requested, with acceptance still unverified.
- AMOUNT/AMOUNT_SENT retain one frozen amount and wait for exact observed review.
- REVIEW shows the matching carrier details and instructs native manual confirmation; no automated Confirm.
- PIN shows manual PIN guidance. A subsequent non-sensitive, nonempty carrier response can show waiting for a usable result. This does not prove PIN entry or completed authentication. A renewed supported PIN prompt restores manual-entry guidance; no timer advances these states.
- RESULT displays carrier wording with financial UNKNOWN. Closure/return remains conservative and never authorizes SUCCESS/FAILED.
- INTERRUPTED now explicitly asks the user to inspect the carrier screen and cancel/check official bank history if unclear. No automatic retry.

No new carrier prompt grammar/menu guesses were added. Existing source/ownership/freshness guards, exact VPA/INR comparison, input revalidation and PIN exclusions remain. The system carrier dialog stays foreground; V4SH does not force itself over it.

## Automated steps versus manual steps

Implemented for supported observed prompts: choose the unique Send Money option, choose the unique UPI ID option, populate/submit the frozen VPA, populate/submit the frozen amount, and observe an exact matching review. Fresh window/package/ownership and prompt checks apply before writing and Send. Action success is only a request; subsequent review supplies input-match observation.

Manual: QR/UPI entry and amount/Review authorization, actual native carrier confirmation, PIN entry, and opening V4SH after closing the dialog where automatic return cannot safely be established. Unsupported prompts stop assistance; compatibility with unobserved remarks/purpose/review variants is not claimed.

## Exact modified files

All paths relative to D:/FestPay:

- app/src/main/java/com/festpay/app/ui/viewmodel/PaymentViewModel.kt — real engine shares existing coordinator journal factory; default protected store unchanged.
- app/src/main/java/com/festpay/app/data/CarrierPaymentFlow.kt — observation-driven waiting-for-result flag, cleared on supported PIN prompt.
- app/src/main/java/com/festpay/app/ui/screens/CarrierPaymentJourneyScreen.kt — honest per-stage request/observation guidance and safe interruption action.
- app/src/main/java/com/festpay/app/ui/screens/ScreenPlaceholders.kt — pending Pay button label.
- app/src/androidTest/java/com/festpay/app/ReviewProductionEngineTest.kt — actual Review-to-real-engine test with synthetic stores/host and intercepted dialer intent.
- app/src/test/java/com/festpay/app/NativeCarrierConfirmationTest.kt — response-driven waiting state regression.
- docs/UI_TO_CARRIER_INTEGRATION_REPORT.md — this report.

No AccessibilityService, journal implementation, permission, PIN input, history design or backend changes in this task.

## Validation and physical limits

JVM: 120/120 PASS. Debug/release builds, debug lint and release vital lint PASS (build/ui-carrier-build.log, 5m 34s). Initial full Android run: 47 baseline cases PASS, new integration fixture failed before Pay due to its context hiding ActivityResultRegistryOwner. Fixture corrected to explicitly retain the real owner; a nullable-owner compilation error was also corrected. Final focused integration run PASS (1 test, 6.718s, build/ui-carrier-integration-final.log). Thus all 48 distinct Android cases passed across the baseline run and corrected focused run; no claim of one full 48-case passing run. Test assertions were retained. Integration fixture intercepts Context.startActivity without delegating to Android, uses uniquely named synthetic encrypted stores and a synthetic session host, and asserts the exact frozen values, durable PROCESSING/reservation before arm, one arm, one *99# request, UNKNOWN outcome and duplicate prevention. It is not proof that the actual service drove Airtel or that the carrier accepted the request.

Prior Redmi/Airtel evidence established native initial-menu visibility, advertised editable/Send actions and modal blocking of V4SH; earlier user tests established QR/manual preparation and balance behavior. No live payment transaction, payment-field acceptance, native payment confirmation/PIN or owned-payment automatic return has been physically established. The existing UNKNOWN reservation blocks every new live payment. Its journal/seal fingerprints must remain unchanged.

Next external prerequisite remains legitimate authorized reconciliation uniquely correlated to the old attempt, followed by a reviewed evidence-consumption path. User acknowledgment, screenshots, balance changes or new test payments cannot release it. See PRODUCTION_PAYMENT_JOURNEY_REPORT.md and AUTHORIZED_CONFIRMATION_CONTRACT.md. No reconciliation or guard bypass is implemented here.

No commit or push.

Final preservation: protected journal and seal fingerprints match before/after tests (build/ui-carrier-before.sha256 and build/ui-carrier-after.sha256). User confirmed Accessibility enabled, connected and instance available after restoration. No further instrumentation or app restart followed restoration.
