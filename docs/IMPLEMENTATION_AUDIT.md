# V4SH implementation audit and checkpoint ledger

## Phase A — source audit before this task's implementation

Baseline includes the earlier balance-mode reset patch and Phase 1 documents. No physical feature is certified by this audit. The prior 38-test/debug/release result predates the changes in this task.

| Area | Current status and responsible files | Remaining work |
| --- | --- | --- |
| Application shell | Existing Kotlin/Compose application, four-tab navigation, branded theme; `MainActivity.kt`, `ui/navigation/*`, `ui/theme/*`, `ui/screens/ScreenPlaceholders.kt`. | Device/navigation/accessibility validation; visual polish after functional checkpoints. |
| QR | Existing CameraX/ML Kit scanner and recipient/amount/review screens in `ScreenPlaceholders.kt`. | Strict URI/request validation and stale-input regression; no physical scanning pass in this audit. |
| UPI payment | `PaymentViewModel.kt` real engine normally uses ACTION_DIAL, returns authentication after timers. Service has VPA navigation. | Durable attempt/coordinator, correct observation-driven UI, no unverified final claims. |
| Mobile payment | No complete mobile-specific request/session/input path demonstrated by the inspected payment orchestration. | Implement only after dependent safety/checkpoint work; physically validate. |
| Balance launch | `startBalanceCheck()` uses ACTION_CALL; screen requests CALL_PHONE. Reset broadcast now includes balance mode. | Validate actual automatic call on Redmi/Airtel. No device result yet. |
| Balance root/Send | Service Stage 1A selects 3 for balance, 1 for payment; Stage 1B excludes balance. Earlier reset patch addresses late reset clearing mode. | Current code marks input complete even when Send returns false; test actual advancement/duplicates. No proof the previous physical issue is fully resolved. |
| Authentication boundary | Service uses text heuristics; currently reads editable node text and logs full screen contents before filtering. | Critical: remove raw diagnostics and prevent editable/password-value reads; validate real node behavior without capturing credentials. |
| Balance parser/UI | `data/BalanceParser.kt` returns first plausible numeric match after broad keywords. Existing balance screen polls global data and renders a result; balance launch creates no payment record. | Reject ambiguous/multiple/unrelated numeric values; event-driven observation/readiness/error handling; physical result/display validation. |
| Payment outcomes | Service substring matcher accepts success inside unsuccessful; progress screen can navigate directly to unconditional success receipt. | UI text cannot authorize financial SUCCESS/FAILED. No approved provider verification source exists; use UNKNOWN. |
| Status check | ViewModel delays and replays last local result. | Remove false implication of bank query. |
| History | `data/TransactionRepository.kt` uses preferences/JSON/apply; errors swallowed. IDs can be fixed placeholders; timestamp is relative text. Details screen uses lastOrNull despite front insertion. | Durable identity, real timestamps, explicit record selection, evidence/provenance, recovery. Local history is not bank history. |
| Carrier history | No reliably validated carrier-history import established. | Conditional only: do not invent importer without accessible real responses. |
| Receive/profile | Existing standard UPI details/QR presentation and preferences profile. | Validate configured identity and end-to-end presentation; no account verification claim. |
| Mock isolation | Mock in main source, selected with runtime debug visibility and mode; results share production history. | Isolate simulation at build/storage boundaries. |
| Session/recovery | Global payment/balance data, reset broadcasts, in-memory booleans and per-destination ViewModels. | Separate session ownership, stale-event rejection, durable duplicate reservation and restart recovery. Larger coordinator must be designed/explained before implementation. |
| Backup/release | Manifest allows backup with template rules; lint enforcement/minification disabled. Signing properties exist locally; committed exposure/compromise not established. | Explicit restore/security policy, release checks/artifact audit. Build success is not integrity proof. |
| Existing tests | Three JVM test classes; some mirror code or assert constants. | Test actual decision/interaction boundaries, secret exclusion, evidence rejection, crashes/duplicates; preserve prior coverage. |

Paths without an explicit directory are under `app/src/main/java/com/festpay/app/`.

## Dependency order

1. Remove sensitive accessibility diagnostics and field-value reads before authentication testing.
2. Verify balance launch/readiness and root→3→Send using the existing reset/routing; fix observed submission/duplicate issues narrowly.
3. Validate PIN boundary, then balance response parsing and display.
4. Remove heuristic financial authority, false receipt claims, artificial status checks, and production simulation.
5. Design and implement the minimum durable coordinator/session ownership needed for safe payment execution; then mobile/QR regression.
6. Carrier history only if actual responses permit reliable parsing; preserve Receive.
7. Full UI/error/release audit, regression and physical validation.

No authoritative bank/PSP integration is assumed. SUCCESS AUTHORIZATION = BLOCKED. UNKNOWN remains the safe financial fallback. Pending physical checkpoints are not PASS, and none of these audit findings certify production readiness.

## Phase B — first implementation checkpoint

Task: accessibility logging and field-reading security prerequisite.

- Root cause: raw screen text was emitted before filtering; input values were collected and even inspected during editable-node selection. Parsed financial values and arbitrary exception messages were also logged.
- Files changed in this checkpoint: `FestPayAccessibilityService.kt`; new `data/AccessibilityTextPolicy.kt`; new `AccessibilityTextPolicyTest.kt`.
- Exact change: remove raw screen/balance/VPA/amount/exception-message diagnostics; retain fixed event labels, enum states, booleans, and generated option digits. Skip input/password nodes and their subtrees during collection and button discovery. Inspect password flags before label collection and stop interaction. Use static-label authentication detection without allowing VPA context to override it; retain root-menu PIN-management labels as non-prompts.
- Tests: 42 JVM tests, zero failures/errors (four new tests); lazy input readers throw if invoked by the policy tests. Root menu, ordinary navigation, PIN/OTP/password, and VPA-context cases covered.
- Build: `testDebugUnitTest assembleDebug assembleRelease --offline` completed successfully. Gradle deprecation warnings remain.
- Physical test: ADB currently lists no connected devices. User is connecting the Redmi; live checkpoint results are pending. No credential screen has been captured and no authentication has been performed.
- Result: software checks passed; real-device security and USSD behavior NOT yet validated. Secret exclusion still depends on actual OEM node exposure; no universal PIN-safety claim.
- Next task: physical readiness and balance launch/root→3→Send checks. Later feature checkpoints are not marked complete.

## Physical retry and balance-result follow-up

The Redmi connected, the debug APK installed, Phone permission was granted, and V4SH Accessibility was enabled. The user reported direct option 3 navigation to the carrier PIN prompt. Fixed diagnostic markers confirmed balance mode=true, option=3, input=true, Send found=true and clicked=true, and PIN_OR_SENSITIVE detection.

The user corrected an initial response: the carrier displayed the balance, but V4SH did NOT. The balance-display checkpoint therefore failed; no end-to-end PASS is recorded.

Root cause traced in diagnostics: after sensitive-screen detection, a transient window failed the loose USSD-header check and the service marked the session UNKNOWN. Terminal-state handling then ignored subsequent result windows.

Focused change: keep the balance authentication/result wait pending through intermediate windows. Capture the root carrier package when selecting the balance option; only consider non-sensitive, non-editable results whose event and active-root packages match that source. Unparseable intermediate responses do not become terminal immediately. Input/password values remain excluded. This package association is not a full session identity or evidence-authenticity guarantee; timeout handling, parser ambiguity and durable recovery still need work.

Files: `FestPayAccessibilityService.kt`, new `data/BalanceObservationPolicy.kt`, new `BalanceObservationPolicyTest.kt`.

Verification: 45 JVM tests passed with zero failures/errors; debug and release builds passed. The new debug APK was installed and V4SH reopened. A fresh user-confirmed physical balance-display test is pending. No PIN or balance value was retrieved for this diagnosis; diagnostics were filtered to fixed markers.

Follow-up physical result: carrier balance appeared, but V4SH remained loading. This retry FAILED the display checkpoint. Added boolean-only result-guard diagnostics to identify source-match/sensitive/editable blocking without exposing content.

During the next retry the user reported option 3 no longer entered. Diagnostics showed a newly connected service selected option 1 with balance mode=false before receiving any new session broadcast. Added an explicit service activation guard: connection/update starts inactive, only a fresh V4SH session broadcast arms event processing, and interruption/destruction disarms it. This prevents service reconnection from acting on an already-open carrier dialog. The 45-test suite and debug/release builds passed again; installed the guard build and restarted V4SH. A clean root→3 retry is pending. No end-to-end balance PASS is recorded, and no later checkpoint is considered complete.

The next no-option-3 retry was traced to Android having disabled/unbound V4SH Accessibility after restart. The user re-enabled it; actual binding was verified before continuing. Option 3 worked again, but the display still loaded.

Boolean-only diagnostics then identified the result guard: sourceMatch=true, sensitive=false, editable=true on the carrier response. The result dialog retains an input control, so rejecting the entire window for having an editable field was too strict. Revised the guard to permit static-label parsing from the associated source while continuing to skip input/password values and reject sensitive prompts.

Tightened BalanceParser for this result path: require one labeled amount with complete decimal/grouped-number syntax; reject menu/authentication/error text, multiple currency values, account/mobile-like integers and malformed values. Do not retain raw carrier text. Added four parser safety tests and updated the observation-policy tests to assert that a retained input value is never requested.

Latest verification: 49 JVM tests passed, zero failures/errors; debug and release builds passed. Latest APK installed without force-stopping; Accessibility confirmed enabled and bound. Fresh end-to-end physical display retry pending. No success claim is made until the user confirms the displayed amount matches the carrier response.

### Physical outcome — 2 November 2026

The final retry emitted BALANCE_RESULT_PARSED=true after the associated carrier returned a non-sensitive static response. The user explicitly confirmed: "Yes, V4SH displays the correct balance." Check Balance therefore passed this physical attempt on the connected Redmi/Airtel configuration, combining device diagnostics and user-confirmed amount agreement. Neither the amount nor PIN was retrieved or recorded.

This supersedes the pending status above for that attempt only. Previous failed retries remain documented. It does not establish repeated-session reliability, all-device compatibility, universal secret exclusion, payment completion, or production readiness. Remaining follow-ups include deterministic session ownership, readiness handling after updates, timeout/unavailable UX, broader parser/device regression, and the separate blocked payment-confirmation work.

## Offline follow-up

The user reported that with data off, the carrier menu opened but manual option entry was required. Device inspection found V4SH Accessibility enabled in settings but listed as crashed/unbound. Sanitized crash inspection identified IllegalArgumentException in service onDestroy at receiver cleanup. This establishes a service-lifecycle defect; it does not establish a carrier requirement for mobile internet.

Changes: guard receiver registration/cleanup, tolerate an already-released receiver without logging exception payloads, track actual service connection, and refuse balance automation with actionable Accessibility guidance if unbound. Balance launch errors now publish an unavailable result rather than leaving an indefinite spinner. Balance presentation uses observable state rather than a polling loop, and timer-derived authentication progress was removed from balance launch.

Offline contract: remove transitive INTERNET permission explicitly in the main manifest. Existing barcode scanning uses bundled ML Kit, fonts are local system fallbacks, profile/history are local, and USSD uses cellular transport. No authoritative offline payment-confirmation source is assumed. Cellular signal, eligibility, and provider support are still required; airplane-mode banking is not promised.

Verification: all 49 tests and debug/release builds passed. Installed-package inspection showed no INTERNET permission. After the user toggled Accessibility, binding was verified and crashed-services was empty. A controlled test set mobile data=0, Wi-Fi=0, airplane mode=0 and reopened V4SH with the service bound. End-to-end offline confirmation is pending the user's observation; do not mark it PASS based on these settings alone. Mobile data was left OFF for the requested test; its pre-test setting was ON.

### Offline balance outcome

The user confirmed: "Option 3 advances and V4SH displays the correct balance offline." This passes the data-off/Wi-Fi-off balance attempt on the tested Redmi/Airtel setup. It does not promise operation without cellular signal.

## Offline payment preparation and safety checkpoint

Implemented after the offline balance confirmation:

- Built-in SQLite attempt/event/reservation journal. Unique immutable UUID, actual machine-readable creation/transition times, INITIATED committed before PROCESSING/external effects, one unresolved reservation, conservative PROCESSING→UNKNOWN recovery, terminal reuse rejected. Financial confirmation transitions remain blocked with no approved source. This is local persistence, not a bank ledger or full evidence integration.
- Shared PaymentViewModel across navigation destinations. Explicit attempt-ID selection for history details; legacy preference history is labeled UNKNOWN and blocks new payments pending review rather than being treated as verified bank history.
- No timer-generated payment authentication; no local fake bank-status query. Real execution returns an unverified outcome. Launch errors remain unverified local initiation problems.
- Mock engine moved to test sources only; production application always uses the real adapter. Runtime mock controls are disabled. Success route renders uncertainty while authorized confirmation capability is unavailable. Carrier result strings cannot grant SUCCESS or definitive FAILED.
- Strict local UPI URI parsing, duplicate-field rejection, exact decimal amount validation, target validation, full QR stale-field reset. Mobile-number entry and review route added with separate mobile method selection; recipient/amount/method travel in the session request rather than being erased by reset.
- Request identity rejects duplicate reset delivery; an in-memory operation reservation prevents concurrent balance/payment menu interaction. This does not authenticate system events or guarantee external idempotency.
- Menu input now attempts the existing Send action after method/recipient/amount entry. Submission failure stops automation with an unclear result rather than automatically repeating it. A session timeout reports uncertainty/unavailability, never authentication or success.
- Receive screen generates a real standard UPI QR locally from the saved valid account, replacing the QR icon. No account identity is invented for an unconfigured profile. QR encoder dependency is bundled at build time. Existing ML Kit scanner uses the bundled model, not an on-demand model download.
- Backup disabled and explicit database/preferences/file exclusions added for cloud backup and device transfer. App INTERNET permission explicitly removed from merged manifests.

### Verification evidence

56 JVM tests passed, zero failures/errors, including complete currently-supported/blocked state-transition policy checks, input rejection, and QR encode/decode round trip. Debug/release/test APKs built successfully. Release DEX inspection found no LocalMockPaymentEngine; release merged manifest contains no INTERNET permission.

Gradle connected-test execution in offline mode was blocked by an uncached UTP test-platform artifact. Direct ADB installation was initially rejected by Android's user installation restriction; after user authorization, the test APK installed. Direct instrumentation on the Redmi passed both PaymentJournalTest cases: reservation persistence/UNKNOWN recovery after database reopen, duplicate creation rejection, and pre-execution cancellation/new identity. These tests used separate test databases and initiated no USSD or payment. They do not simulate every process-death/effect boundary.

Physical offline QR-to-review and mobile-to-review checks are pending user feedback. Actual mobile/QR banking execution, repeatability, Receive QR scan by another app, carrier-history import, and full-screen polish have not passed physical validation in this checkpoint. No real payment was submitted during this audit. Production confirmation remains blocked.

Remaining material gaps: authorized evidence/reconciliation source, institution/distribution eligibility, encrypted financial-record storage/key lifecycle, exhaustive restore/corruption and process-death fault tests, robust SIM selection, stronger session/event association and real-device compatibility matrix. No production-readiness claim is made. No Git commits or pushes performed.

### QR follow-up and corrected user observation

QR compatibility now permits omitted/blank/zero requested amounts to proceed to manual positive amount entry, while negative/malformed amounts remain rejected. Format-only diagnostic codes are recorded without payloads. 57 JVM tests and debug/release/test APK builds passed. An additional OfflineBarcodeTest passed on the Redmi using the bundled ML Kit decoder and a generated synthetic UPI QR with the app INTERNET permission denied. This tests the decoder, not camera framing or a real payment.

The user initially reported a format error, then explicitly corrected the observation: QR capture worked, displaying the recipient name and automatically extracting the UPI ID. Recipient extraction is therefore user-confirmed; any name is QR-supplied, not bank-verified. Review details and full offline camera-to-review regression remain pending the next user check. No payment was initiated for this checkpoint.

The user subsequently confirmed: "Review shows the correct details offline." QR camera capture → recipient extraction → amount entry → Review therefore passed this user-observed offline preparation attempt. This does not validate carrier payment navigation, authenticate the QR name, or confirm a transfer. Mobile-number Review and Receive presentation are the next pending physical checks.

### Receive and mobile Review observations

The user saved their UPI ID and had a friend scan the generated Receive QR; the friend's payment app resolved it to the user's bank account. Receive QR external resolution therefore passed that user-reported test. No actual incoming payment was tested or claimed.

The user reported the mobile-number Review number or amount was incorrect. This checkpoint FAILED and blocks proceeding to live mobile payment. The exact mismatch is being clarified without collecting personal details; no source fix or root-cause claim is made solely from this report. The displayed "Mobile recipient (unverified)" label is intentional and does not constitute an account-name lookup.

The user clarified that the concern was the unavailable account-holder name, then confirmed the actual mobile number and amount were correct. Mobile entry → amount → Review therefore passed the offline preparation check. No incorrect-number/amount defect was established. There is no authorized offline account-name lookup; carrier banking navigation remains a separate unverified checkpoint.

The user authorized continuing with a live QR navigation test, explicitly stopping before PIN entry. Readiness was verified: V4SH Accessibility bound, no current crashed service, mobile data=0, Wi-Fi=0, Phone permission granted. The user was instructed to tap Pay only once and not retry; the durable unresolved-reservation limitation was disclosed before authorization. Awaiting the physical navigation outcome; no transfer or payment-success claim.

### Corrected launch-block observation and UPI-only scope

The user clarified the observed failure: QR extraction and Review worked, but Pay showed "Another cellular operation is active" on the unclear-status screen. This is a pre-initiation guard rejection, not evidence that the carrier advanced to an input or that this tap created a durable payment attempt. Existing unresolved attempts, if any, remain reserved.

Source inspection found an independent ViewModel balanceRunning flag cleared by the balance screen's completion effect. Leaving that screen could retain the flag after the service completed. Removed this duplicate flag; service operationActive now owns concurrency, including acquisition before the balance broadcast and immediate release when a payment observation becomes UNKNOWN. Active carrier sessions remain blocked. Busy/invalid preflight now returns IDLE and stays on Review with an explanatory error, rather than navigating to a financial UNKNOWN page. This source-level defect is established; the precise flag value during the user's earlier observation was not captured.

At the user's request, replaced mobile-number payment entry with validated manual UPI ID entry. QR payment remains available. The payment ViewModel accepts only valid UPI IDs; no offline phone-to-account lookup is claimed. Added regression coverage that preflight rejection creates no selected attempt/history and does not manufacture a financial UNKNOWN state. Fresh physical navigation remains pending; no payment-success or end-to-end certification is claimed.

Verification: final debug APK build passed; all 59 JVM tests passed with zero failures/errors, including the two new preflight tests. These tests do not establish live carrier navigation or confirm financial outcomes.

### Latest physical observation correction and diagnostic clarity

The user initially selected "carrier stops at another field" and "remarks/purpose/confirmation," then explicitly corrected this: no carrier dialog appeared, and V4SH showed "Payment status unclear." No remarks-navigation implementation was made on the basis of the withdrawn report. Accessibility remained bound and Phone permission granted. Available static logs did not establish a carrier launch. The exact displayed description is pending; the failure cause is not yet established.

Improved error separation: unavailable/unreadable storage stays on Review; an existing reservation explicitly identifies the earlier unresolved attempt and states that the current tap did not launch the carrier. The unclear-status screen now displays the engine's actual safe launch message when no operation error exists, instead of hiding it behind a generic fallback. No reservation is cleared, no automatic retry is added, and no financial outcome is inferred.

User-supplied screenshots confirmed the previous combined storage/unresolved guard message and a stale bank-verification error on Review. They establish that the reported tap was blocked before a new carrier launch request; they do not distinguish storage failure from an existing reservation. Review now inspects these conditions on entry without creating an attempt or launching USSD, clears stale cross-screen error text, and disables Pay when blocked. The source guard still checks again at initiation to preserve race protection.

The screenshots also exposed misleading QR-verification wording and a clipped verification button. Replaced the verification badge/claim with an explicit account-holder-not-verified notice, shortened the button label, and allowed its height to grow with text. No private values from the screenshots are copied into diagnostics or this report. Physical readiness diagnosis and carrier launch remain pending.

The diagnostic update passed the debug build and all 59 JVM tests, and was installed on the Redmi with data preserved. The next checkpoint is to enter Review without tapping Pay and report the readiness category; no payment or USSD launch is required for that check.

### Readiness result: unresolved reservation confirmed

The user entered Review without tapping Pay and reported "Earlier payment attempt remains unresolved." This confirms that the current blocking path is the existing durable reservation, rather than unavailable/unreadable storage. It does not establish whether the earlier attempt reached the carrier, was authenticated, or settled. New carrier launches must remain blocked under the frozen UNKNOWN/duplicate-payment policy. No reservation was cleared, no state was relabeled CANCELLED/FAILED/SUCCESS, and no replacement payment was attempted.

Further live payment execution is blocked pending an approved reconciliation mechanism for the earlier attempt. V4SH currently has no authorized transaction-specific bank/provider confirmation adapter or safe unresolved-attempt resolution workflow. App reinstall, data deletion, a different recipient, another local ID, or a separate production history cannot be used to bypass that reservation. Offline preparation, Receive, balance observation and history presentation can still be checked independently. Full end-to-end payment operation remains unverified and must not be claimed complete.

### History refresh and selected-record correctness

The user reported that recent actions were absent from history and an older record had previously been visible. Blocked pre-initiation taps intentionally create no new financial attempts; their absence from transaction rows is expected. The older saved unresolved record must remain accessible. This report does not establish that the user's stored records were deleted or that every recent tap created an attempt.

Source inspection found that Home/Transactions only invoked a one-time repository initializer; returning to an already initialized model did not reread storage. Added resume-based refresh to Home, Transactions and Details, plus an explicit Refresh history action. History now reads the complete replacement list before modifying displayed records, and shows an explicit read-error message instead of silently presenting an empty list. Existing rows remain visible on refresh failure. No fabricated rows are added for blocked taps.

Details now displays only the record matching the selected explicit attempt ID; removed fallback to the current QR/payment request. A missing record has an explicit unavailable state. Added the persisted creation timestamp to Details and formatted actual ISO timestamps in the device timezone. Unparseable legacy placeholders are labeled unavailable rather than replaced with the current time.

Verification: debug app/test builds and all 61 JVM tests passed. A new PaymentHistoryTest passed on the Redmi using a separately named synthetic database and preferences. It verified rereading a saved INITIATED-to-UNKNOWN change, identity/timestamp retention, readiness blocking for the reserved attempt, and retaining displayed history/reservation after a deliberately malformed synthetic state caused a read failure. Exactly one synthetic attempt remained; no USSD launched and no user transaction database or preferences were altered by this test. The update was installed with application data preserved. Physical confirmation of the user's history remains pending.

Physical follow-up, 3 November 2026: the user selected "Earlier record and details appear" after being asked to refresh Transactions and inspect the saved record's status and Created timestamp. History access and selected-record details therefore passed this user-observed checkpoint. No new carrier launch, bank reconciliation, settlement, or payment-success verification was performed. The unresolved reservation remains protected.

### Offline setup and profile corrections — 3 November 2026

The user confirmed that no authorized provider integration is available, then requested the next work. Payment confirmation/reconciliation remains blocked; this checkpoint addresses independent offline functions.

Fixed a false-save UI path: UPI Details previously showed Saved after a superficial contains-@ check even when the ViewModel rejected the value. Profile validation now rejects malformed/overlong IDs, blank/overlong names and control characters; surrounding spaces are normalized. Profile persistence returns an explicit result after a SharedPreferences commit, and the UI only marks Saved on success. Save errors are displayed separately from payment errors. Editing resets the success label, and initialized/restored form values are retained. Saving a profile explicitly does not claim account ownership verification or bank linkage.

Manual UPI entry retains its input across recreation and no longer truncates pasted recipient IDs; invalid/overlong IDs are rejected rather than silently shortened. Validation guidance explicitly excludes mobile-number lookup.

Replaced setup placeholders with a Profile-accessible Payment Readiness screen showing current Phone permission, actual Accessibility connection, the local reservation/storage block and unavailable confirmation. Permission requests and Accessibility settings now use real Android actions. SIM guidance opens connection settings and explicitly does not claim to select/verify the bank-registered SIM. Returning from settings refreshes the displayed checks; none of these actions launches USSD.

Removed unsupported claims of an active mobile network, an implemented biometric app lock and release-candidate status. Replaced the no-op Clear cache control with an explanation that unresolved attempts remain protected. Offline help distinguishes cellular signal from internet; QR help describes only the supported upi://pay request format.

Verification: debug app/test APKs built; all 63 JVM tests passed. ProfilePersistenceTest passed on the Redmi with separately named synthetic preferences/database. It verified invalid-save rejection, committed normalized values after a fresh ViewModel reload, unchanged saved values after invalid replacement, Receive QR round-trip identity and absence of payment attempts/reservations. It did not simulate a storage-full failure or a full process kill. User profile/history were not changed by the isolated test. The updated app was installed with data preserved. Manual UPI-to-Review and new readiness/settings navigation await physical confirmation. No live payment was initiated and no Git commit or push was performed.

Physical follow-up, 3 November 2026: with data/Wi-Fi off as requested for the checkpoint, the user confirmed "Statuses appear and settings opens" for Profile → Payment Readiness → Open Accessibility settings, and "Manual UPI details are correct on Review" for manual UPI ID → amount → Review. These offline UI/navigation checkpoints passed on the Redmi. They do not certify SIM routing, carrier payment navigation, authentication, settlement or reconciliation. The earlier unresolved-attempt reservation and disabled Pay remain expected; no live payment was requested for these checks.

### Remaining local-feature corrections and regression pass

On the user's request to finish the app, audited the remaining visible controls and corrected further gaps:

- Support submission was a no-op that falsely claimed a request was logged. It now opens Android's share chooser for the user's note, with no automatic sending, configured recipient, support inbox or ticket/delivery claim.
- Camera permission recovery opens V4SH's Android settings; manual fallback opens recipient UPI entry rather than changing the user's Receive profile. Permission state and the scan-once guard refresh on resume. A reusable scanner is closed when disposed; paused/disposed callbacks cannot navigate. Camera startup/analysis errors use static diagnostic markers and visible startup failure feedback.
- Previously inactive Help controls now navigate to Help. The root layout applies and consumes Scaffold padding so bottom navigation cannot cover content. Removed unused raw-response USSD tester and production QR simulation/engine-mode controls.
- Removed the progress-screen polling loop and its carrier-presentation-to-financial-route decisions. Success/failure pages require a matching financial state and an available confirmation mechanism; with the current absent mechanism they show uncertainty. The SQLite history reader and history row presentation downgrade unsupported stored SUCCESS/FAILED labels to UNKNOWN: local state alone is not provider evidence. Existing reservations are preserved.

Final verification: debug, release and test APK builds passed offline on the host; 64 JVM tests passed with zero failures/errors. All 9 Android tests passed on the Redmi: package context, two Compose UI tests (unsupported success screen and invalid-profile false-save rejection), bundled offline barcode decode, history refresh/read-failure retention, durable journal reopen/recovery, unsupported stored terminal-label rejection, cancellation before execution/new identity, and profile persistence/Receive identity. Synthetic tests used separate preferences/databases or did not initialize storage; no banking operation was launched and no user payment database was edited by the tests. The debug update was installed with user data preserved. Repeat camera scanning, help and share-chooser manual checks remain pending.

Release APK creation does not complete authorized confirmation/reconciliation, encrypted record storage/key lifecycle, exhaustive crash/restore testing, SIM qualification or release hardening. The earlier unresolved attempt remains protected. Full end-to-end live payments are not complete, and no such claim is made. See APP_COMPLETION_STATUS.md for the current feature/limitation matrix. No commit or push was performed.

Final physical follow-up, 3 November 2026: the user confirmed "Both scans work" for offline scan → Review → return → rescan, and "Help works and share chooser opens" for Help → Contact Support → generic test note → Android chooser. These manual camera/navigation/handoff checkpoints passed on the Redmi. The check requested cancellation without sending; no support delivery or ticket creation is claimed. The tested local-feature checkpoints are complete; authorized payment execution/confirmation and the listed production requirements remain incomplete.

## Production-readiness master-prompt run — validation completed with external blockers

Session/readiness/release follow-up: acknowledged session UUID ownership now gates carrier launch; balance/payment payloads and operation leases are separated, source/system/window/time checks precede label reads, ordered navigation requires a matching payment review, and UNKNOWN cannot be cancelled from another draft. Added permission-minimal SIM/airplane/hardware/operator observations without claiming bank registration. Added normal-use affirmative Accessibility disclosure, explicit non-tool classification and revocation; no institutional/store approval is inferred. The clean suite passed 74 JVM and 24 Redmi tests. One preceding combined targeted runner stalled; the activity test passed in a fresh process and then the full suite passed. Supplemental consent/readiness tests are compiled, not yet executed.

Release follow-up: shrinking/resource shrinking and release lint/error gates enabled. Offline lint initially lacked its cached tool; normal dependency fetch resolved it and release build/lint passed (0 errors, 42 warnings, 1 hint). Signed APK verification and 16 KB zip alignment passed. All packaged 64-bit ELF LOAD segments meet 16 KB alignment; only upstream 32-bit barcode binaries have 4 KB alignment. No binary patch/ABI removal. Updated compile/target to API 36 per current official submission requirements; full target-migration validation is in progress. Real default profile/history migration and final-build offline balance remain pending user confirmation. No real unresolved reservation was cleared.

Re-established unchanged baseline before new source changes: 64 JVM tests, 9 Redmi Android tests, debug/release/test builds passed. Read the requested completion/audit/requirements/architecture and relevant MCP architecture/payment/security/navigation/roadmap specifications. Existing working tree was preserved; no commit/push/reset was performed.

Workstream A: official API investigation found no authorized transaction-correlated confirmation mechanism usable by the current integration. The user has no provider integration. See PAYMENT_CONFIRMATION_INVESTIGATION.md. This is scoped to reviewed mechanisms, not a claim that no institutional solution could ever exist. SUCCESS/FAILED authorization remains blocked.

Workstream B: bounded failure-first wording classification replaces the remaining diagnostic substring classification; all such wording still maps financially to UNKNOWN. Added journal request validation, collision injection coverage, duplicate/idempotent transition and forbidden-transition checks, and INITIATED interruption recovery to UNKNOWN. Added separate nullable provider-reference/provenance fields and explicit evidence/operation presentation; no provider reference is manufactured. UNKNOWN has the requested status/history/done controls and no retry. The state checkpoint compiled and JVM tests passed. The expanded Redmi run passed journal cases but had one no-Compose-hierarchy startup failure in an existing profile test; its targeted rerun passed. This first-run failure is retained rather than reported as a clean full-suite pass.

Workstream C: implemented Keystore AES-GCM protected SQLite schema v2, profile/legacy preference encryption, v1 migration preserving reservations/identities, encrypted integrity seals, missing-key/corruption failure handling and fail-closed UI handling. Six isolated encrypted-storage tests passed on the Redmi. User-store migration has not yet been physically confirmed; tests used separate synthetic stores. A coordinator edge case that could overwrite a profile failure with journal readiness was found during review and fixed; its additional regression/full-suite run is pending. See ENCRYPTED_STORAGE_DESIGN.md for actual boundaries and limits. Later ordered workstreams remain in progress; no production-completion claim is made.

C checkpoint follow-up: the coordinator latch regression passed. The first full encrypted suite was 17/18 because an older history assertion expected the guard ID to remain readable after ciphertext tampering. Updated that assertion to require unavailable protected storage, retained raw reservation-row count, retained previously displayed history/error, and rejection of another creation. The clean rerun passed all 18 Android tests; 68 JVM tests passed. No safety assertion/test was removed, and no corrupted record was promoted to a financial outcome.

Workstream D: added transaction-boundary fault injection with no-op production defaults, a debug-only synthetic instrumentation harness and a non-exported synthetic lifecycle Activity. The exception matrix/stale-version tests passed (2/2). Host orchestration passed all five actual process-kill boundaries and both PROCESSING/UNKNOWN force-stop cases (7/7), then an actual Redmi reboot/manual unlock passed verification (1/1). ActivityScenario recreation, observed landscape change and cold activity restart passed (1/1). No banking calls were made; real user stores were not opened by these fixtures. Actual LMK scheduling and real cloud/device-transfer restore remain NOT TESTED, not implied by process-kill or exclusion checks. See RECOVERY_VERIFICATION.md. Session-isolation audit is next.


Final master-run checkpoint, 3 November 2026: compile SDK 37 / target SDK 36 / minimum SDK 26. SDK 36 installation failed due full C:; only this run's verified .temp/PackageOperation01 and new platforms/android-36 failed-install directories were removed. No existing SDK or personal files were removed. Build used the installed official SDK 37. An internal lint analysis failure cleared in a fresh process with java temporary files on D:. Removed the existing AccessibilityPolicy suppression; lint/error gates remained enabled. Final debug/release/test builds and lint passed (0 errors, 41 warnings, 1 hint). Final 74 JVM and complete 28-device-test suite passed. New UI regression verifies manual entry replaces an earlier QR's recipient/amount/note, preserves unresolved blocking, and Help navigates to a note-only user-controlled chooser. No test sends a message or payment.

Final physical evidence: actual data/Wi-Fi off, airplane off and bound V4SH service were verified before the balance check. User confirmed option 3 advances and matching balance displays after manual carrier PIN. User confirmed two QR scans/Review correct, all Receive/history/details/Help/chooser screens open, and retained profile/earlier transaction after migration. Manual flow initially reported incorrect; follow-up established that the displayed UPI ID is the same and the concern was the expected unverified-name label. User separately confirmed the amount also matches. No bank lookup was fabricated. Following the final test run, user toggled Accessibility off/on for final readiness; binding metadata is checked separately.

Final artifact checks: signed APK verifies; zipalign -c -P 16 4 passes; all 8 packaged 64-bit libraries have >=16384 ELF LOAD alignment. Release manifest has target 36, no INTERNET, no debuggable flag, backup disabled and no debug fixtures/instrumentation. R8 mapping excludes recovery and mock classes; APK has no signing-credential file. Keystore properties are ignored/untracked, with no matching tracked history found; no compromise assertion. Existing real unresolved reservation was never cleared. No new live payment was initiated. Provider confirmation/institutional authorization/store approval and the documented unqualified environments remain BLOCKED/UNKNOWN. No commit/push/reset was performed. APP_COMPLETION_STATUS.md is the current consolidated status; preceding paragraphs preserve historical checkpoints and failures.

## Final gap-closure run — 3 November 2026

Re-read requested reports/MCP/current code and re-established unchanged 74-JVM/28-Android/debug/release/test/lint baseline. Bounded official research added actual Razorpay payment lookup, Cashfree order-payment lookup, ICICI corporate status capability and Android public USSD/SIM APIs; none supplied authorized correlation for current consumer *99# attempts. Added AUTHORIZED_CONFIRMATION_CONTRACT.md with five explicit answers, evidence/identity/finality/replay requirements, future adapter/schema/credential/connectivity decisions and old-attempt recovery limits. No adapter or provider credentials invented.

UI audit found Help lacked explicit manual-PIN, QR-name, dual-SIM and support chooser boundaries; expanded these in ScreenPlaceholders.kt and replaced a guaranteed-authentication claim with the actual manual provider boundary and lack of verification. Source review also found startBalanceCheck could write a balance consent error before checking a busy payment owner. Added ReadinessConsentTest.busyPaymentCannotBeOverwrittenByBalanceConsentError; it failed before correction (expected IDLE, actual UNKNOWN). Moved the existing busy guard before any consent/readiness presentation write. Normal menu 3/Send/PIN/result path and all financial state rules remain unchanged.

Extended EncryptedStorageTest for unsupported schema/malformed legacy recipient with retained attempt/reservation rows, UssdSessionTransportTest for stale-owner release, and classifier variants. The intermediate 29-test run stalled on a profile-key-loss case and was stopped; root cause of that runner stall was not established and it is not counted as PASS. The focused pre-fix regression failure is retained as defect evidence. Final fresh complete suite: 74 JVM / 30 Android PASS. Final debug, signed release, test APK and release lint PASS (0 errors, 41 warnings, 1 hint); signature, 8 64-bit ELF/ZIP alignment and release isolation/permission/credential checks PASS. No test/gate suppressed.

Actual synthetic host recovery repeated all 5 kill boundaries and 2 force-stop cases PASS. Fresh user-authorized reboot/manual unlock followed by VerifyReboot PASS (UNKNOWN, reservation retained, no replay). User re-enabled Accessibility; actual binding and data/Wi-Fi-off/airplane-off were verified before fresh balance test. User confirmed automatic option 3 and matching balance, two DIFFERENT QR reviews without stale data, correct manual ID/amount with Pay blocked, Receive/profile/history/details retained after reboot, new Help guidance and support chooser cancelled without sending. PIN/balance/payment values were not retrieved.

16 KB investigation identified official ps16k API-36 x86_64 image; installed host acceleration check returned 6, no usable guest established. Partial download on ignored D: build directory stopped; no checksum/runtime PASS claimed. Device pages are 4096. No native patch, SDK removal or personal-file deletion. Public signing digest comparison shows debug/release identities differ, so signed-release runtime/upgrade remains unqualified; no destructive reinstall that would remove the real guard was attempted.

Created factual ENGINEERING_DATA_INVENTORY.md (including SDK diagnostics, OS saved-instance copies, support sharing and retention/deletion limits), COMPATIBILITY_QUALIFICATION.md, UI_CLAIM_AUDIT.md and GAP_CLOSURE_VALIDATION.md; appended distribution/confirmation/security/recovery/release evidence without rewriting historical checkpoints. Repaired corrupted generated GIT_STATUS.md containing an embedded prior report, then validated snapshot structure/path count. PRODUCTION_READINESS_REPORT.md now begins with current A–F results; previous report is explicitly historical.

Current result: NOT PRODUCTION READY. Confirmation mechanism NOT FOUND; attempt correlation UNAVAILABLE; SUCCESS/FAILED authorization BLOCKED; existing real unresolved attempt PROTECTED. New live payments, automatic retries, fake references/reconciliation, manual guard unlock, commit/push/reset were not performed. Provider/institution/distribution/privacy/support gates and broad runtime/device qualification remain explicit.


## Final service-lifecycle observation — 3 November 2026

After the successful balance/QR/Review/history/Help checks, a final metadata read found V4SH enabled but unbound and the app process absent. Android listed the service under crashed services, but no app crash stack was available; that lifecycle label alone does not establish an application crash. The user confirmed closing/clearing V4SH. Filtered ApplicationExitInfo metadata included USER REQUESTED / FORCE STOP (and separate OTHER KILLS BY SYSTEM entries), supporting a manual-stop explanation without proving every exit cause.

The user re-enabled Accessibility; a final read verified V4SH bound and its process running. No subsequent test/restart was performed. Record this Redmi limitation: after clearing/stopping the app, users may need to re-enable the service; a settings switch alone is not proof of binding. Current preflight fails closed while disconnected. No forced auto-enable, battery-policy bypass, credential automation or new permission was added. Arbitrary background/OEM lifecycle recovery remains unqualified. An older LOW_MEMORY exit metadata entry was also seen; it is diagnostic evidence, not a controlled successful LMK recovery test or established cause of the earlier stalled runner.

## Core payment preparation and recipient identification — 3 November 2026

Focused request: QR/manual VPA preparation, actual bank-name research, safe Review and unchanged payment integrity. Read current requested reports and actual parser/scanner/manual entry/Review/engine/ViewModel/service/session/navigation/policy/journal code. The existing 74-JVM/build baseline passed. Attempted unchanged 30-Android baseline stalled and was stopped; historical full-30 PASS is not relabeled as a clean run here.

Official research found NPCI OC-101A 2025-26 bank/CBS-name display rules, sponsor-bank TPAP onboarding guidance, Oracle PSP ReqValAdd descriptions and Paytm's scoped merchant validity API. No authorized current NPCI wire schema, V4SH relationship/credential/channel or actual bank beneficiary name was obtained. Exact eight-question findings and primary citations are in BENEFICIARY_VERIFICATION_INVESTIGATION.md. Carrier *99# -> 1 -> 3 -> VPA was NOT TESTED because the user's protected real unresolved reservation does not permit a new test; no external/manual bypass.

Root cause 1: QR pn entered SharedPaymentState.payeeName and was displayed as the main recipient heading, despite an unverified disclaimer. PaymentRecipient.kt now carries verified name/status/source/time as one immutable object. The shared default resolver has no provider, no network and returns honest UNAVAILABLE/INVALID_UPI_ID. No QR/username/contact-derived name is promoted. An authorized adapter trust boundary has no production implementation; synthetic adapters are test-only.

Root cause 2: Review displayed global mutable draft fields and read them again on Pay. Shared PaymentPreparation now freezes canonical amount/recipient/note/revision; Review renders and supplies the same snapshot, checks it again and disables Pay after a draft/verification change. New selection IDs reset amount/note editor state, and lookup generation/request/VPA checks prevent old replies, including the same address being reused. Financial provider verification remains separate; PaymentIntegrityPolicy and encrypted journal/USSD semantics are untouched.

Application files changed in this run: data/PaymentRecipient.kt (new), data/PaymentInputValidator.kt, data/UpiQrParser.kt, ui/screens/MobilePaymentScreen.kt and payment-specific sections of ui/screens/ScreenPlaceholders.kt. No Help/Profile/Receive/Support, theme, permissions, release configuration, new payment method or unrelated architecture changes. QR explicit zero/negative amount rejection intentionally supersedes the earlier zero-as-unspecified behavior per the new user requirement; the existing test is updated, not dropped. Notes are marked local/not sent because no qualified USSD remarks transport exists.

Tests: new BeneficiaryVerificationTest covers normalization/validity, unavailable/verified TEST_ONLY boundary, invalid/provider-error/timeout, malformed/missing source, stale/wrong-VPA/wrong-request replies, concurrent recipient changes, frozen/expired Review and independence from financial outcomes. Android adds QR-pn exclusion/frozen-Review invalidation and updates existing manual/stale-QR assertions to the new shared contract. Existing coverage preserved.

Initial updated UI run stalled in an existing lifecycle fixture; no new-code cause established. With user-readied unlocked phone and actual Accessibility temporarily OFF, the next complete suite passed 31/31. Final JVM passed 82/82. Debug/test/release builds and unchanged lint gate passed: 0 errors, 41 warnings, 2 hints. Test-only beneficiary/payment fixtures absent from release DEX. Current release hash 76C1AFBA84ACECB9C8222259F26718DD6F6A9E96A828D008B01FEE034F8CEE71. No failing test suppressed and no root cause of the earlier environment/runner stalls invented.

User restored Accessibility and confirmed fresh offline QR and manual Check beneficiary name/amount/Review: correct address/amount, honest UNAVAILABLE status, no invented name and Pay remains blocked by the existing real record. No real bank name, carrier payee response, payment initiation, PIN or settlement result is newly certified. Manual PIN boundary source/tests and earlier physical evidence remain unchanged. No real attempt or guard was changed, no retry enabled, no commit/push. NOT PRODUCTION READY. Current payment matrix is CORE_PAYMENT_FUNCTIONALITY_REPORT.md; earlier global reports retain historical checkpoints.


## Final device-metadata qualification

After the user's successful preparation checks, a final read showed V4SH in Enabled Accessibility services but not in Bound services; mobile data was OFF and Wi-Fi was ON at that final snapshot. The user had reported restoring Accessibility and running the preparation checks offline. The snapshot is not a simultaneous measurement of the earlier user actions, so those are recorded as user-reported offline checks, not a new timed connectivity certification. Automated offline decoding/no-INTERNET coverage remains PASS.

Do not claim current carrier readiness from an enabled settings switch. The service must actually be bound before any otherwise-legitimate USSD operation, and the real unresolved reservation independently continues to block a new payment. No additional app restart, service bypass or carrier launch was performed. QR/manual preparation remains independent of Accessibility binding. The cause of the final disconnect was not established in this focused task; the previously documented stop/rebind limitation remains relevant.
