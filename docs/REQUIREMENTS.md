Phase 1 Architecture Freeze — Based on validated repository audit at commit 28065ba.

# V4SH Requirements Baseline

Status: Proposed freeze baseline, pending review. MUST and MUST NOT are release-blocking obligations; SHOULD requires a documented reason for deviation; MAY is optional and cannot weaken a MUST.

## CURRENT STATE

The validated implementation has timer-derived authentication state, local-only status checks, post-result SharedPreferences persistence, raw accessibility logging, shared payment/balance session data, and production-source mocks. It does not satisfy the target requirements below. See `PaymentViewModel.kt:59–95,151–248`, `TransactionRepository.kt:10–58`, and `FestPayAccessibilityService.kt:114–160,299–317` under `app/src/main/java/com/festpay/app/`.

## TARGET STATE: MUST

| ID | Category | Requirement and acceptance evidence |
| --- | --- | --- |
| F01 | Functional | MUST validate canonical recipient, bounded positive exact-decimal amount, INR currency, route, and SIM before execution; reject malformed requests in coordinator tests. |
| F02 | Functional | MUST obtain explicit authorization for frozen payment details; changes require renewed review and a new attempt where appropriate. |
| I01 | Integrity | MUST create and durably commit an INITIATED attempt before external interaction. Every payment attempt MUST receive an immutable unique local attempt ID. The ID MUST never be reused for another attempt. Persistence MUST enforce uniqueness; an ID collision MUST fail attempt creation before any external payment interaction. A provider/reference ID is separate and MUST NOT replace the local attempt ID. Fault tests show no launch on failed writes or collisions. |
| I02 | Integrity | MUST enforce all financial transitions centrally and atomically journal their cause, prior state, new state, and evidence references. Invalid transitions are rejected. |
| I03 | Integrity | MUST support UNKNOWN as a legitimate outcome, preserving uncertainty until qualifying new evidence resolves it. |
| I04 | Integrity | MUST correlate evidence to the exact attempt, recipient, amount, currency, provider identity/reference, and final outcome semantics; mismatches cannot authorize SUCCESS. |
| I05 | Integrity | MUST retain evidence provenance and policy version for any future SUCCESS decision. Local IDs and provider references remain separate. |
| I06 | Integrity | MUST persist actual machine-readable timestamps for attempt creation, authorization, financial transitions, observations, and evidence receipt/evaluation. Provider-reported timestamps MUST be stored separately with provenance. NEVER persist relative display strings such as "Today, Just now" as event timestamps. Relative labels such as "2 minutes ago" MAY be generated only at presentation time. A timestamp alone MUST NOT be treated as proof of transaction correlation or financial finality. |
| R01 | Reliability | MUST serialize submission through a durable reservation covering INITIATED, PROCESSING, and UNKNOWN; restart and concurrent coordinator tests demonstrate no second launch. |
| R02 | Recovery | MUST recover unresolved attempts from the journal and quarantine restored attempts; no recovery path invokes payment execution automatically. |
| R03 | Reliability | MUST persist PROCESSING and possible-interaction metadata before invoking external effects. Ambiguous crashes become UNKNOWN. |
| R04 | Reliability | MUST isolate payment and balance session ownership and serialize use of telephony; stale events cannot modify another operation. |
| S01 | Security | MUST meet `SECURITY_REQUIREMENTS.md`, including secret exclusion, scoped accessibility, storage protection, evidence integrity, replay prevention, and signing controls. |
| P01 | Privacy | MUST define retention, deletion, backup, transfer, access, and incident policies before release; ordinary deletion cannot remove unresolved duplicate guards. |
| U01 | UX | MUST render status and receipt details from a selected durable attempt, with verification source identified when confirmation is claimed. |
| U02 | UX | MUST distinguish initiation errors from financial failure and distinguish authentication milestones from financial states. |
| T01 | Testing | MUST test the actual coordinator/state machine/journal for crashes at each effect boundary, duplicate events, competing starts, late evidence, persistence failures, replay, restore, and contradictory observations. |
| T02 | Testing | MUST test false-success inputs including `unsuccessful`, negation, mixed success/failure text, unrelated windows, stale sessions, and amount/recipient mismatches. None may grant SUCCESS. |
| T03 | Testing | MUST validate supported real-device/carrier/bank/SIM configurations and release artifacts with documented results before release. |
| T04 | Testing | MUST verify that production artifacts cannot execute mocks, persist simulated outcomes, or emit sensitive content. |
| T05 | Testing | MUST test every permitted financial-state transition and every forbidden financial-state transition against the central state machine. Forbidden transitions MUST be rejected without changing financial state or releasing the reservation. |
| T06 | Testing | MUST explicitly test stale, replayed, mismatched-attempt, untrusted, and inconclusive evidence rejection as authorization for SUCCESS. None may grant SUCCESS; duplicate evidence MUST have no second financial effect. An inconclusive observation may only preserve uncertainty or cause a permitted transition to UNKNOWN. |
| T07 | Testing | MUST prove UNKNOWN survives process death/restart, never expires automatically into SUCCESS or FAILED, never releases the duplicate-payment reservation automatically, and never automatically triggers payment execution. |
| T08 | Testing | MUST prove duplicate events cause neither duplicate submission nor duplicate financial transitions. MUST inject process death at every documented durable-write/effect boundary and assert the specified recovery state and absence of automatic re-execution. |
| T09 | Testing | MUST prove failed verification never downgrades an already authorized durable SUCCESS. |

## TARGET STATE: MUST NOT

| ID | Prohibition |
| --- | --- |
| N01 | MUST NOT claim SUCCESS without trustworthy, transaction-specific evidence from an authorized confirmation source. |
| N02 | MUST NOT automatically retry an unresolved payment or automatically convert UNKNOWN to SUCCESS. Only an explicit evidence-gated reconciliation decision can resolve UNKNOWN. |
| N03 | MUST NOT treat timers, UI clicks, dialer launch, PIN prompts, local state, generic USSD text, debit messages, balance changes, or user notes as standalone completion proof. |
| N04 | MUST NOT store, log, fabricate, or automatically enter UPI PINs, OTPs, passwords, or other authentication secrets. |
| N05 | MUST NOT use AccessibilityService as a financial authority or allow screens to authorize outcomes independently. |
| N06 | MUST NOT present local attempt IDs as bank/provider references or merge simulation into production history. |
| N07 | MUST NOT let timeout, verification unavailability, app restart, ordinary deletion, or backup restoration authorize retry or downgrade durable SUCCESS. |
| N08 | MUST NOT label local launch failure as bank rejection, or claim cancellation after possible submission without authoritative non-execution evidence. |

## TARGET STATE: SHOULD

- SH01: SHOULD minimize retained evidence to authenticated structured fields and protected provenance; avoid raw UI content.
- SH02: SHOULD provide accessible, localized uncertainty guidance validated for the supported market.
- SH03: SHOULD use a centrally maintained compatibility matrix and deny unsupported configurations.
- SH04: SHOULD provide privacy-preserving diagnostics and actionable storage/recovery errors.
- SH05: SHOULD use exact monetary types and schema versioning throughout the domain and journal.

## TARGET STATE: MAY

- M01: MAY retain user-supplied references as explicitly unverified notes, without unlocking retries or financial transitions.
- M02: MAY add an authorized verification adapter after its trust contract and permissions are approved.
- M03: MAY support provider idempotency only where a documented enforceable contract actually exists.
- M04: MAY provide clearly labeled simulations in separate nonproduction builds and storage.

## UNRESOLVED QUESTIONS

VERIFICATION SOURCE: UNRESOLVED

No bank, NPCI, or PSP integration is assumed. Provider permissions, authenticated evidence format, matching rules, finality, retention periods, key management, and supported configurations require reviewed decisions. Until approved evidence is available, SUCCESS AUTHORIZATION = BLOCKED and UNKNOWN = SAFE FALLBACK for possible submissions.

Implementation status: Documentation only. No application source code was modified.
