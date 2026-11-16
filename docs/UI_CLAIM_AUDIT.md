# User-facing claim audit and integrity review

Reviewed current source on 3 November 2026; not an assertion about every OEM's rendering or a future provider integration.

| Claim / surface | Evidence in current implementation | Decision |
| --- | --- | --- |
| Payment successful / completed receipt | PaymentSuccessfulScreen immediately renders the UNKNOWN screen while confirmationAvailable=false; real engine has no SUCCESS result; journal rejects terminal authority. | Guarded/unreachable current financial receipt, not a live capability. KEEP gate; future evidence must be attempt-specific, not a global flag flip. |
| Provider confirmed failure | PaymentFailedScreen has equivalent capability/state guard; no current authorized FAILED source. | Guarded/unreachable. Do not label transport/initiation failure as financial failure. |
| Unsuccessful / success wording | Failure-first bounded classifier plus carrierOutcome always UNKNOWN; service's payment result classifier returns UNKNOWN. | CONFIRMED no substring-to-financial-SUCCESS path. BALANCE_SUCCESS only presents a balance observation and never updates the journal. |
| QR recipient verified | Review says request-supplied details/account holder not verified; names originate from QR. | Truthful. Expanded Help to state QR names are not independent bank verification. |
| Manual recipient unverified | Manual screen carries entered VPA/amount without an account-holder lookup. | Truthful and expected; synthetic/manual user checks verify entered details, not ownership. |
| UPI details saved | Save screen navigates to saved state only after repository commit confirmation; invalid save test rejects false success. | Supported local persistence claim; not bank linking/verification. |
| Transaction ID/reference | Details and receipt label local UUID V4SH Attempt ID; Bank/UPI Transaction Reference absent. | KEEP separate namespaces/provenance; no inferred external ID. |
| Payment status check | checkStatus reports bank verification unavailable and returns current local state; no bank query. UNKNOWN exposes no payment retry. | Truthful unavailable explanation; no resolution/unlock claim. |
| Stop waiting / cancel | Stopping observation preserves UNKNOWN/reservation. markCancelled accepts only an IDLE draft and cannot cancel an earlier UNKNOWN attempt. | KEEP; financial and operational cancellation differ. |
| SIM/coverage/bank verified | Readiness reports independent coarse observations and cannot-verify text. SIM guidance says Android/carrier controls routing. | No guarantee. Added same dual-SIM boundary to Help; no fake selector. |
| Every transaction authenticated securely | Security copy previously implied a guaranteed provider result. V4SH cannot establish it. | MODIFIED to manual provider/carrier PIN boundary and explicit inability to verify authentication/settlement. |
| PIN never requested/stored/logged | No app credential fields; password/editable subtree exclusion and static diagnostics; manual carrier boundary. | Supported implementation boundary on tested configuration; no universal proof about arbitrary OEM mislabeling. Do not capture PIN screens. |
| Offline operation | Bundled QR model, no INTERNET permission, user-confirmed data/Wi-Fi-off balance/preparation. USSD still needs cellular signal. | KEEP scoped claim. External sharing/provider services are separate and may need internet. |
| Support submitted/ticket/delivery | Only explicit ACTION_CHOOSER with user-authored note; no inbox/backend/automatic recipient. | No ticket/delivery claim. Help now spells out this boundary. |
| Balance proves payment received/sent | BalanceParser and BALANCE_SUCCESS are isolated observations; no financial outcome transition consumes a balance delta. | No such authority allowed. Help explicitly warns against it. |
| Backup safe / app clear resolves payment | Backup disabled/excluded; no history-deletion/clear-reservation control. All-data deletion/uninstall cannot preserve a universal guard or establish settlement. | KEEP exclusions and disclose actual limits; never use reinstall as reconciliation. |
| Play approved / institutional approval | Non-tool consent/service metadata exists; no authorization evidence supplied. | No approval claim. External gates remain required. |

## Payment engine / journal audit

Production constructs RealUssdPaymentEngine directly. LocalMockPaymentEngine exists only under src/test and is excluded from app variants. The real engine awaits an exact owned-service initialization acknowledgment before ACTION_CALL, returns UNKNOWN, and never retries. It does not query an authorized financial source. No app-owned backend/development endpoint or credential source is present in main Kotlin code.

PaymentAttemptJournal.create persists a unique UUID, INITIATED, creation event and reservation atomically before the coordinator arms external interaction. Shared locking and SQLite uniqueness reject concurrent/duplicate creation; unresolved records block new effects. Guarded transitions/version checks and idempotent same-state events work. Production never calls a reconciliation/release path. The journal's explicit INITIATED -> CANCELLED facility is for a never-submitted draft and is not invoked as an UNKNOWN unlock; UNKNOWN cannot transition to CANCELLED.

App restart restores readable INITIATED/PROCESSING to UNKNOWN; invalid/missing/corrupt storage blocks rather than resets. Existing real reservation is untouched. Full uninstall/clear removes local data, so no lifetime duplicate-proof claim is made. A future integration must address that external reconciliation boundary.

Session acquisition/release is identity-scoped; wrong/late ACKs cannot arm a later session. Review found one operational presentation gap: startBalanceCheck checked consent before existing ownership, so a consent failure could overwrite a busy payment's presentation. A regression and minimal check-order fix address this without changing the successful option-3/Send/PIN/balance path or any financial rule. Exact test outcome is recorded in IMPLEMENTATION_AUDIT.md.

Search covered all main Kotlin/XML payment authority/cancel/retry/timeout/balance terms and all user-visible verified/successful/completed/reference/submission claims. MCP documents describe historical desired Room/SIM/definitive-provider capabilities; the current user instruction and inspected SQLite/no-selector/no-provider implementation take precedence. No historical frozen spec was silently rewritten into evidence of an integration.
