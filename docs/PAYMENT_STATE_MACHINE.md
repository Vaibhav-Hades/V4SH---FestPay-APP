Phase 1 Architecture Freeze — Based on validated repository audit at commit 28065ba.

# V4SH Financial State Machine

## CURRENT STATE

`app/src/main/java/com/festpay/app/ui/viewmodel/PaymentViewModel.kt:26–28` defines IDLE, PROCESSING, SUCCESS, FAILED, UNKNOWN, CANCELLED, and AUTHENTICATION_REQUIRED. INITIATED is absent. Lines 151–210 assign state directly, persist only after execution returns, and use local results for status checks. This document defines a replacement contract; it does not describe an implemented state machine.

## TARGET STATE: state definitions

The requested seven names are documented below. IDLE is a pre-attempt presentation state, not a persisted financial attempt. The six attempt states are INITIATED, PROCESSING, SUCCESS, FAILED, UNKNOWN, and CANCELLED. This explicitly reconciles the earlier audit's six public financial states with the requested IDLE name; review must approve this distinction.

| State | Means / entry evidence | Does NOT mean |
| --- | --- | --- |
| IDLE | No selected active attempt; user may prepare input. Global journal checks still apply. | No earlier unresolved obligation, or permission to bypass the reservation. |
| INITIATED | Validated, user-authorized immutable attempt durably committed; external interaction is not yet armed. | Instruction submitted, authenticated, or completed. |
| PROCESSING | External interaction is durably armed and may have started; committed before invoking the adapter. | Bank acceptance, PIN completion, debit, or success. |
| SUCCESS | Approved authenticated transaction-specific evidence establishes completed payment under documented finality semantics. | A success-looking screen, local reference, or a guarantee against later reversal. |
| FAILED | Approved definitive correlated evidence establishes rejection/non-completion. | Timeout, lost session, unavailable verifier, or local launch error. |
| UNKNOWN | Interaction may have occurred and final outcome cannot be established; uncertainty reason and latest known milestone are durable. | Failure, cancellation, permission to retry, or automatic eventual success. |
| CANCELLED | Durable cancellation with proven non-execution, either before interaction was armed or from an authorized source. | Merely leaving a screen or closing USSD after possible submission. |

AUTHENTICATION_REQUIRED is not a financial success/failure state. It maps to an operational authentication-request observation while the attempt remains PROCESSING or UNKNOWN.

## Operational milestones

Each event has an event ID, attempt/session ID, operation type, observation time, source, trust level, and allowed minimized payload. Recording a milestone never bypasses state policy.

| Milestone | Meaning and limit |
| --- | --- |
| REQUEST_CREATED | Frozen request committed with INITIATED; no external effect. |
| USSD_LAUNCHED | Launch action reported; not proof the carrier accepted a request. |
| USSD_OBSERVED | Associated dialog observed; not payment evidence. |
| AUTHENTICATION_REQUESTED | Associated authentication prompt observed; no PIN capture. |
| AUTHENTICATION_COMPLETED | Reserved for a legitimate observable source that can establish this milestone without secrets; currently unavailable and never inferred from timer or disappearance. |
| SUBMISSION_OBSERVED | An instruction may have been submitted; not acceptance or completion. |
| CONFIRMATION_RECEIVED | Candidate confirmation received, pending trust and correlation checks. |
| VERIFICATION_STARTED | An actual authorized verification operation began; unsupported today. |
| VERIFICATION_COMPLETED | Evaluation finished with an explicit verdict, including inconclusive; not necessarily success. |

Operational milestone ≠ financial confirmation. An observed USSD screen must not automatically authorize SUCCESS.

## Permitted transitions

All changes use a central compare-and-transition transaction with expected prior version, durable cause, evidence references, and reservation update. Same-state observations are journal events, not repeated financial transitions.

| From | To | Required condition |
| --- | --- | --- |
| IDLE | INITIATED | Atomic validated attempt creation and reservation; IDLE itself is not written as an attempt. |
| INITIATED | PROCESSING | Durable intent-to-interact and exclusive session reservation committed before external invocation. |
| INITIATED | CANCELLED | User cancellation wins the atomic race against arming interaction; non-execution proven. |
| INITIATED | UNKNOWN | Recovery detects inconsistent/uncertain submission boundary; quarantine and investigate. |
| PROCESSING | SUCCESS | Approved final success evidence. Currently blocked. |
| PROCESSING | FAILED | Approved definitive failure evidence. |
| PROCESSING | UNKNOWN | Timeout, interruption, uncertain effect, or inconclusive evidence. |
| PROCESSING | CANCELLED | Authorized non-execution evidence; a local close action is insufficient. |
| UNKNOWN | SUCCESS | New qualifying evidence and explicit audited reconciliation decision. Never a default or timer conversion. |
| UNKNOWN | FAILED | New qualifying definitive failure evidence and audited reconciliation decision. |

Every unlisted normal transition is invalid, including INITIATED→SUCCESS, PROCESSING→INITIATED, UNKNOWN→INITIATED/PROCESSING/CANCELLED, SUCCESS→UNKNOWN, FAILED→PROCESSING, and CANCELLED→PROCESSING. No terminal attempt is reused. Local initiation errors are operational events: retaining INITIATED is permitted ONLY when execution has NOT been armed; pre-arming cancellation requires proof. Once PROCESSING is durably committed, a local launch error MUST NOT return the attempt to INITIATED. After PROCESSING, uncertainty MUST remain UNKNOWN unless a specifically permitted transition has qualifying evidence. Non-submission observations alone MUST NOT authorize an automatic relaunch. UNKNOWN MUST never automatically trigger another payment. This deliberately avoids adding INITIATED→FAILED merely to encode launch errors.

## Terminal states and evidence conflicts

SUCCESS, FAILED, and CANCELLED are terminal in the normal attempt lifecycle. UNKNOWN is unresolved, not conclusively terminal. Reversal/refund/dispute and authoritative contradictions are separate linked audit records subject to a separately approved correction policy; no silent overwrite. Duplicate final evidence is idempotent. Uncorrelated or unauthenticated late responses are rejected without altering financial state.

VERIFICATION SOURCE: UNRESOLVED

SUCCESS AUTHORIZATION = BLOCKED

UNKNOWN = SAFE FALLBACK

## Submission boundary and process death

The journal cannot atomically commit with a carrier. Phase 1 therefore chooses conservative uncertainty: commit PROCESSING before the external call, then never replay it automatically. This may leave UNKNOWN even when no instruction actually left the device.

| Crash/interruption point | Recovery action |
| --- | --- |
| Before durable attempt creation | No external effect permitted; return to input/IDLE. |
| After INITIATED, before interaction is armed | Retain INITIATED only with proven non-execution; explicit user continuation or cancellation. |
| After PROCESSING commit, before actual submission | UNKNOWN unless qualifying evidence authorizes a transition explicitly listed for the current state. Never return to INITIATED or replay. Local non-submission observations alone do not authorize relaunch; UNKNOWN→CANCELLED remains forbidden. |
| During submission | UNKNOWN; retain reservation and latest durable milestone. |
| After submission | UNKNOWN absent qualifying final evidence. |
| After possible provider acceptance | UNKNOWN; neither timeout nor process death proves failure. |
| Before confirmation | UNKNOWN; independently verify where supported. |
| After candidate confirmation, before durable verified result | Re-evaluate safely retained non-secret evidence, or remain UNKNOWN if insufficient; never resubmit. |
| After verified result committed | Restore durable terminal result; failed refresh does not downgrade it. |

## Timeouts, verification failure, restart, and cancellation

Timeout and interrupted USSD produce UNKNOWN after possible interaction, never FAILED. An unavailable verifier cannot change a known terminal result; unresolved attempts remain UNKNOWN. Restart recovers journal and reservation before enabling execution. Backup-restored entries are non-executable and quarantined until reviewed reconciliation establishes their status.

Stopping waiting records an observation-stop event and durable uncertainty when appropriate. It does not cancel external payment. Pre-submission cancellation must race atomically with execution arming; only one wins. No coroutine or late local timer may overwrite a later durable state.

## Duplicate submission and unresolved recovery

One durable active-payment reservation covers INITIATED, PROCESSING, and UNKNOWN across all payees, amounts, and SIMs. No new attempt may execute while it is held. Concurrent starts and repeated events cannot acquire it twice. A resolved attempt releases it only atomically with the qualifying transition. Retrying a definitively failed payment is a new attempt with deliberate authorization, never re-execution of the old attempt.

Ordinary deletion, logout, reinstall assumptions, user notes, and elapsed time cannot establish bank finality. Local protection cannot prevent independent external payments or guarantee idempotency across devices. Provider-enforced idempotency is not assumed. Long-lived UNKNOWN has no automatic expiry or override in Phase 1.

## UNRESOLVED QUESTIONS

Approval is required for evidence-source contracts, finality, conflicts/corrections, migration/restore treatment, retention, and support for indefinite unresolved reservations. No source currently authorizes SUCCESS or establishes provider failure semantics. Architecture review must approve the conservative all-payment blocking policy before implementation.

Implementation status: Documentation only. No application source code was modified.
