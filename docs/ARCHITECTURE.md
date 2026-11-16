Phase 1 Architecture Freeze — Based on validated repository audit at commit 28065ba.

# V4SH Phase 1 Architecture

Status: Documentation freeze baseline proposed for review. No architecture approval, implementation completion, institutional permission, or production certification is implied.

## CURRENT STATE

At validated commit `28065ba`, the Android application combines Compose presentation, in-memory ViewModel orchestration, telephone intents, accessibility heuristics, and SharedPreferences JSON history. Important boundaries are missing:

- `ui/viewmodel/PaymentViewModel.kt:59–95`: fixed delays produce AUTHENTICATION_REQUIRED; launch errors become FAILED.
- The same file, lines 151–210: attempts are recorded after execution, only PROCESSING blocks another start, stop-waiting does not persist, and check-status replays local state.
- `FestPayAccessibilityService.kt:23–36,75–104`: payment and balance share global data cleared by resets.
- The same service, lines 114–160,299–317: full text is logged before filtering; success substring matching takes precedence even for `unsuccessful`; classification precedes USSD-window filtering.
- `data/TransactionRepository.kt:10–58` and `data/room/TransactionEntity.kt:1–2`: ordinary preferences and a dummy Room file, not an attempt journal.
- `ui/navigation/MainNavHost.kt:53–62` and screen defaults: no explicitly shared attempt owner across destination ViewModels.
- `ui/screens/ScreenPlaceholders.kt:684–693,2028–2063,2375–2416`: runtime debug controls and unconditional receipt wording; mocks share history.

All paths above are relative to `app/src/main/java/com/festpay/app/`. Signing exposure was not confirmed in reachable Git history. Backup rules and release gates are unfinished. Runtime behavior and institutional eligibility remain unvalidated.

## TARGET STATE: conceptual flow

```text
UI
  ↓
Payment Coordinator
  ↓
Attempt / Transaction Journal
  ↓
Payment Execution Adapter
  ↓
USSD / Telephony Interaction
  ↓
Observation Layer
  ↓
Verification / Evidence Layer
  ↓
Financial State Machine
  ↓
Receipt / History
```

This is a responsibility flow, not permission for the journal to launch payments or for a receipt to set state. The coordinator controls effects. The state machine evaluates commands before execution as well as final evidence; every accepted transition is atomically committed to the journal. UI and history subscribe to committed projections.

| Layer | Responsibility | Boundary |
| --- | --- | --- |
| UI | Input, review, explicit authorization, attempt-specific presentation and honest guidance. | Cannot assign financial outcomes; no independent receipt inference or unscoped mutable payment data. |
| Payment Coordinator | Validate requests, create/freeze attempts, acquire reservation, arm execution, route observations, recover and reconcile. | No timer-based milestones, direct guessed outcomes, or effect before durable commit. |
| Attempt / Transaction Journal | Atomic versioned attempts, transitions, reservations, minimized observations, evidence references and recovery records. | Persistence is not proof of bank execution. Failed/corrupt writes fail closed. |
| Payment Execution Adapter | Execute one explicitly authorized, owned interaction; return transport/action observations and initiation errors. | Cannot return invented authentication or financial completion; never autonomously retry. |
| USSD / Telephony Interaction | Supported cellular launch and user/provider-controlled authentication. | Carrier interaction is not a general bank-status API; SIM routing and availability require validation. |
| Observation Layer | Scoped non-sensitive session-associated observations and permitted UI actions. | Accessibility is not a financial authority; raw content and secret-capable fields are excluded. |
| Verification / Evidence Layer | Authenticate approved source evidence, validate correlation/finality, deduplicate and produce a policy verdict. | Currently unsupported. No fallback from absent integration to text heuristics. |
| Financial State Machine | Centrally enforce permitted transitions and evidence prerequisites with expected record version. | Neither adapters nor screens bypass policy. |
| Receipt / History | Render selected durable attempt and separately labeled verified provider references/provenance. | Local history is not a bank statement; receipt wording cannot exceed evidence semantics. |

### What is allowed to authorize SUCCESS?

Only trustworthy, transaction-specific evidence from an authorized confirmation source.

VERIFICATION SOURCE: UNRESOLVED

SUCCESS AUTHORIZATION = BLOCKED

UNKNOWN = SAFE FALLBACK

No bank, NPCI, or PSP API is specified or assumed to exist. An eventual adapter must demonstrate authorization, technical access, authenticity, transaction matching, and completed-payment semantics. Generic USSD text, a PIN prompt, timer, UI click, debit notification, user reference, local state, and balance movement cannot independently authorize SUCCESS.

## Attempt lifecycle and effect boundary

1. Validate canonical request and explicit authorization; atomically create an immutable unique local attempt ID, frozen details, INITIATED state, REQUEST_CREATED event, and active reservation. Every payment attempt MUST receive this ID, which MUST never be reused for another attempt. Persistence MUST enforce uniqueness. An ID collision MUST fail attempt creation before any external payment interaction. A provider/reference ID is separate and MUST NOT replace the local attempt ID.
2. Atomically verify ownership/version and commit PROCESSING with possible-external-interaction intent before calling the adapter.
3. Invoke at most once under that reservation; append correlated, minimized observations. A crash between commit and invocation remains conservatively unresolved rather than triggering replay.
4. Route qualifying evidence to the state machine and commit the decision, evidence references, and reservation changes atomically. If no approved source can establish an outcome after possible interaction, commit UNKNOWN.
5. Rebuild presentation from committed records on restart, preserving terminal outcomes and unresolved reservations. A process or screen is never the durable owner.

INITIATED→PROCESSING is an intentional conservative write-before-effect boundary, not a claim that submission happened. Local launch errors remain distinct operational errors. See `PAYMENT_STATE_MACHINE.md` for exact transitions and crash cases.

## Conceptual journal model

| Record | Required fields / purpose |
| --- | --- |
| Attempt | Immutable unique local attempt ID enforced by persistence and never reused, canonical recipient/name provenance, exact amount, INR currency, reviewed route/SIM, authorization time, creation time, financial state, expected version, execution-arm metadata. Provider/reference IDs remain separate and never replace the local ID. |
| Transition | Prior/new state, cause, source/actor, time, evidence/policy references, version; append-only history. |
| Session/event | Independent session ID, payment/balance operation type, attempt link where applicable, event ID, milestone, source, time, trust level, minimized payload. |
| Evidence | Evidence ID, authorized source identity, source reference separate from local ID, authenticated fields, correlation/finality verdict, integrity metadata, policy version. No secrets. |
| Reservation | Durable exclusive active-payment ownership; created and released transactionally. |
| Reconciliation/recovery | Verification availability and attempts, inconclusive reasons, interruptions, restore quarantine, conflicts and linked corrections. |

Actual machine-readable timestamps MUST be persisted for attempt creation, authorization, financial transitions, observations, and evidence receipt/evaluation. Provider-reported timestamps MUST be stored separately with provenance. NEVER persist relative display strings such as "Today, Just now" as event timestamps; relative labels such as "2 minutes ago" may be generated only at presentation time. A timestamp alone MUST NOT be treated as proof of transaction correlation or financial finality.

Database/encryption technology, migrations, key management, and evidence retention remain approval decisions. The target requires transactional durability and protected integrity; renaming SharedPreferences to a journal would not meet it. Migration of legacy records must label them unverified and never promote old SUCCESS or placeholders into trusted evidence. Legacy placeholder times must be treated as unavailable timestamps, never fabricated into historical event times.

## Duplicate strategy and session ownership

Phase 1 conservatively blocks all new payment execution while INITIATED, PROCESSING, or UNKNOWN owns a durable reservation, regardless of recipient, amount, or SIM. No timeout, user note, or ordinary deletion releases it. Resolution or proven cancellation is required. This can block indefinitely without an approved source; product review must explicitly accept that consequence. No manual override is specified.

Each payment and balance operation has its own immutable session identity and data owner. A shared telephony arbiter serializes their external interaction; a balance request cannot reset an active payment. Session initialization is acknowledged before use and stale events are rejected. Payment references cannot be inferred from balance results. Local controls cannot guarantee external idempotency or prevent independent payments in other apps/devices.

## Production/test, storage, and release boundaries

Retain Compose/navigation structure, QR scanning technology, mandatory review, and honest uncertainty guidance. Rebuild orchestration, attempt storage, state ownership, evidence gates, identity, recovery, and history projection. Remove production mock execution, raw accessibility logging, heuristic financial authority, and fake status refresh. Preserve simulation only in separate nonproduction artifacts/storage.

Backup/transfer policy must exclude secrets and executable session authority. Restored records are non-executable and unresolved history is quarantined; restore must not silently remove duplicate guards. Release gates include enforced checks, signed-artifact inspection, simulation exclusion, secret-safe diagnostics, and real-device fault testing. Signing configuration presence does not prove compromised keys.

## Phase 1 Freeze Decisions

- IDLE represents pre-attempt presentation and is not a persisted payment outcome.
- INITIATED is the first persisted payment-attempt state.
- An unresolved UNKNOWN attempt globally blocks another payment attempt unless and until an explicitly authorized reconciliation mechanism exists. Its existence alone does not release the reservation: qualifying evidence must resolve the attempt through a permitted transition. There is no manual override or automatic release.
- UNKNOWN may remain unresolved indefinitely if no authoritative confirmation source exists.
- No automatic retry is permitted after uncertainty.
- V4SH currently does NOT have an established authoritative payment-confirmation mechanism.
- Therefore SUCCESS authorization remains BLOCKED until such a mechanism is established and qualifying attempt-specific evidence is validated.
- The architecture may be frozen with UNKNOWN as the safe fallback, but production SUCCESS claims remain blocked.
- This documentation freeze does NOT mean the current application is production-ready.

These are the explicit scope decisions of this documentation baseline; formal review/approval and Phase 2 entry gates remain separate. The INITIATED/PROCESSING reservations also continue to block other payment execution as specified above.

## UNRESOLVED QUESTIONS

- Which institutions authorize the proposed USSD assistance, and which distribution rules permit it?
- Which authorized source, if any, is technically accessible, including without internet? What finality and definitive-failure semantics does it guarantee?
- What supported bank/carrier/device/language/SIM matrix can be demonstrated?
- Which storage/key technology, retention periods, migration rules, restore reconciliation, and incident/support owners are approved?
- How will terminal contradictions, reversals, and indefinitely unresolved reservations be handled outside the normal lifecycle?
- Will product owners explicitly accept initiation-only scope and indefinite UNKNOWN blocking if verification cannot be established?

These are unresolved dependencies, not implemented integrations. Runtime and external validation are separate from document approval.

# Phase 2 Entry Criteria

All items below require recorded review evidence; none is marked satisfied by creating these files.

1. Phase 1 architecture documents reviewed and approved, including explicit acceptance of remaining external limitations.
2. Financial state machine frozen, including IDLE as pre-attempt presentation and six persisted attempt states.
3. Security requirements frozen with acceptance evidence defined.
4. Attempt lifecycle and write-before-effect boundary frozen, including crash and legacy-record recovery.
5. Duplicate-payment strategy defined and conservative global unresolved blocking accepted.
6. Evidence model defined, including provenance, matching, authenticity, finality, replay, and conflict rules; unavailable source capability explicitly represented.
7. Payment verification mechanism established OR product scope explicitly accepts UNKNOWN without claiming confirmed payment. The latter does not authorize SUCCESS.
8. Production/test separation defined, including artifact and storage isolation.
9. Backup/restore strategy defined, including key loss, corruption, quarantine, and reservation recovery.
10. Release security requirements defined, including logging removal, signing controls, checks, and device validation.
11. Owners and validation plans assigned for institutional permission, distribution eligibility, compatibility, privacy/retention, and unresolved-payment support. Production release remains separately gated.

An authorized and technically accessible payment-confirmation mechanism must be established before V4SH can truthfully authorize SUCCESS.

If this cannot be established, the architecture must explicitly preserve UNKNOWN rather than fabricate confirmation. Implementation may start only after the applicable document decisions are approved; production payment confirmation remains blocked independently.

Implementation status: Documentation only. No application source code was modified.
