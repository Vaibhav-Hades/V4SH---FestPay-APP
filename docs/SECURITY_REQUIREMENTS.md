Phase 1 Architecture Freeze — Based on validated repository audit at commit 28065ba.

# V4SH Security Requirements

## CURRENT STATE

Raw Accessibility logging is a critical defect to be removed during implementation. `app/src/main/java/com/festpay/app/FestPayAccessibilityService.kt:119–126` collects and logs full node text before sensitive-screen filtering; lines 365–372 do not exclude password nodes. The config enables window-content retrieval without package restriction. Actual PIN visibility varies by runtime; universal claims of secret exclusion are unsupported.

Transactions use ordinary private SharedPreferences JSON (`data/TransactionRepository.kt:10–58`); no application-level encryption or evidence integrity design was demonstrated. Backup is enabled with template rules (`app/src/main/AndroidManifest.xml:9–12`). Release lint enforcement/minification are disabled (`app/build.gradle.kts:45–58`). Mock execution lives in main source and shares transaction storage.

`keystore.properties` exists locally with nonempty signing fields. It is ignored and untracked, and committed exposure was NOT confirmed by the validation of reachable history. This does not establish that the signing key is compromised. No credential values belong in these documents, logs, or support bundles.

## TARGET STATE: mandatory controls

| ID | Area | Required control and acceptance evidence |
| --- | --- | --- |
| SEC01 | UPI PIN protection | Never store, log, fabricate, capture, or automatically enter PINs or other secrets. Avoid reading sensitive nodes; fail closed where a safe observation boundary cannot be established. Validate on supported real devices. |
| SEC02 | Accessibility | Restrict approved packages/windows and operation lifetime; require explicit user enablement and disclosure. Associate every event with an owned session. Unknown or sensitive screens halt automation without inferring outcome. No unrestricted screen harvesting. |
| SEC03 | Logging | Remove raw node text, secret-capable payloads, recipient/amount debug dumps, and uncontrolled exception payloads from diagnostics, analytics, and crash reports. Use allowlisted event codes and minimized identifiers. Test injected secrets through every diagnostic sink. |
| SEC04 | Local storage | Define encrypted sensitive storage, device-bound key protection, access controls, atomic writes, schema migration, corruption handling, and key-loss behavior. Key loss or corruption blocks execution; it never makes unresolved attempts safe to retry. Technology/key lifecycle require review. |
| SEC05 | Evidence integrity | Verify source authenticity independently of local storage. Preserve approved structured evidence, source identity, digest/signature where supplied, receipt/evaluation times, policy version, and verdict. Local encryption or a local hash alone cannot authenticate bank facts. |
| SEC06 | Provenance | Store provider references separately from immutable local attempt IDs. Retain who supplied each field and whether it is verified, observed, simulated, or user asserted. No simulated evidence in production storage. |
| SEC07 | Correlation | Match evidence to frozen recipient, exact amount, currency, provider identity/reference, and attempt context. Time proximity alone is insufficient. Ambiguous matching cannot authorize success. |
| SEC08 | Duplicate prevention | Acquire durable reservation before effects; preserve it through UNKNOWN, crashes, restore, and ordinary deletion. Serialize payment execution across UI instances and SIM changes. |
| SEC09 | Replay prevention | Deduplicate event/evidence IDs, enforce expected state/version, authenticate future callbacks, reject stale/wrong-session input, and prevent reuse of terminal attempts. Provider idempotency is used only if its contract is established. |
| SEC10 | Backup/restore | Define explicit exclusions for secrets and executable session state. Restored history cannot restore execution authority. Reconstruct/quarantine unresolved reservations before enabling payment. Test cloud and device transfer on supported versions; no auto-resubmit. |
| SEC11 | Signing credentials | Keep secrets and keys outside tracked code and distributable archives. Restrict access and provisioning, avoid secret output, and document recovery/rotation. Assess historical sharing before alleging compromise or deciding incident response. |
| SEC12 | Release isolation | Production builds cannot contain reachable mock execution or debug mode switches; simulated storage and IDs are separate in nonproduction. Verify final artifacts and signing identity. Enforce reviewed lint/security gates; shrinking is defense in depth, not proof of safety. |
| SEC13 | Auditability | Append attempt-associated transition/event/evidence records with reason, timestamps, actor/source, prior version, and integrity metadata. Audit payloads exclude secrets. Corrections are explicit linked records, not silent rewrites. |
| SEC14 | State integrity | Only the central policy may authorize atomic financial transitions. UI/service/timers cannot grant outcomes. Journal failure blocks external effects. Terminal evidence cannot be downgraded by failed refresh. |
| SEC15 | Data minimization | Retain only necessary canonical request, events, and approved evidence fields. No raw PIN screens or unrestricted USSD transcripts. Define retention, deletion, access, support export, and unresolved-record handling before release. |
| SEC16 | QR/request validation | Allowlist scheme/action and fields; handle duplicate parameters, malformed encoding, length limits, unsupported currencies, misleading labels, invalid VPA structure, nonfinite/negative/overprecision amounts, and substitutions. Revalidate in coordinator and freeze reviewed details before execution. |
| SEC17 | Session ownership | Separate payment and balance operation IDs/data; serialize telephony access. Reset acknowledgments and stale-event rejection prevent one operation clearing or confirming another. |
| SEC18 | Verification | SUCCESS requires trustworthy, transaction-specific evidence from an authorized source. USSD strings, local IDs, balances, SMS/debit reports, and user notes cannot independently satisfy this requirement. |

## Threat and failure boundaries

Protect against repeated UI actions, malicious QR payloads, unrelated accessibility windows, text containing negated success, replayed/late events, process death between journal and carrier, lost storage keys, stale backup restoration, and diagnostic disclosure. Device compromise and user execution through another app remain external limitations; do not claim local controls provide universal duplicate prevention.

Financial authenticity, local storage integrity, and confidentiality are distinct properties. An authenticated provider response may still be insufficient if it describes pending debit rather than completed payment. A protected local record cannot convert a guessed observation into evidence.

## UNRESOLVED QUESTIONS and release evidence

VERIFICATION SOURCE: UNRESOLVED

SUCCESS AUTHORIZATION = BLOCKED

UNKNOWN = SAFE FALLBACK

Key management/storage technology, permitted accessibility behavior, provider permissions, finality semantics, retention, restore reconciliation, incident owners, and supported device scope require approval. Before release, collect actual security/fault-test and artifact evidence; the current README's safety claims and device-test assertions do not independently satisfy these requirements.

Implementation status: Documentation only. No application source code was modified.
