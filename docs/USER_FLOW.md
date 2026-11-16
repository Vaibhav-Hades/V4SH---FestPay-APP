Phase 1 Architecture Freeze — Based on validated repository audit at commit 28065ba.

# V4SH User Flows

## CURRENT STATE

Existing screens display authentication after a timer, offer a local-only status refresh, and can render unconditional success/settlement wording. Separate destination ViewModels and global mutable session data do not provide reliable attempt ownership. These flows are not approved confirmation behavior. Source: `PaymentViewModel.kt:151–248`, `ScreenPlaceholders.kt:2208–2285,2375–2416,2551–2568`, and `ui/navigation/MainNavHost.kt:53–62` under `app/src/main/java/com/festpay/app/`.

## TARGET STATE

All screens address an immutable attempt ID and render the journal's financial state. UI navigation cannot establish success. The following are target flows; conditional provider verification is not currently available.

### 1. Normal payment

Enter recipient and amount; validate route and bank-registered SIM; review immutable details; explicitly authorize. Commit INITIATED and the reservation. If storage fails, show “Payment could not be started” and do not launch. Persist PROCESSING before external interaction. Observe permitted navigation and leave authentication to the user. If no qualifying confirmation is available, display UNKNOWN rather than a receipt claiming settlement.

### 2. QR payment

Scan using the existing QR technology. Validate scheme/action, field uniqueness, VPA structure, exact positive amount, INR currency, and supported parameters. Treat supplied names as unverified labels. Reject invalid data before execution. Show all accepted details for review; scanning alone never authorizes payment. Then follow the normal flow.

### 3. Authentication required

Only a suitably associated observation may show “Complete authentication in your provider's payment screen.” AUTHENTICATION_REQUESTED is an operational milestone, not a financial result. V4SH does not capture or enter the PIN. Stopping waiting changes observation behavior; it does not cancel a potentially submitted payment. A timer cannot claim that a PIN prompt appeared or authentication completed.

### 4. Payment successfully verified — conditional capability

This flow is blocked today. Only approved, authenticated, correlated final evidence can let the state machine commit SUCCESS and expose a success receipt. Show the frozen recipient, amount, currency, confirmation source, verified time, and separately labeled provider reference if available. Do not infer settlement beyond the source's approved semantics. A local attempt ID is labeled “V4SH attempt ID.”

### 5. Payment failed

An authorized correlated definitive failure may commit FAILED. Show the verified reason and source. A deliberate new payment requires fresh authorization and established retry safety. A local launch problem is shown as an initiation error; it is not a bank decline. Retaining INITIATED is permitted ONLY when execution has NOT been armed, with explicit user continuation or proven pre-arming cancellation. Once PROCESSING is durably committed, a local launch error MUST NOT return the attempt to INITIATED. After PROCESSING, uncertainty MUST remain UNKNOWN unless a specifically permitted transition has qualifying evidence under PAYMENT_STATE_MACHINE.md. Non-submission observations alone MUST NOT authorize an automatic relaunch. UNKNOWN MUST never automatically trigger another payment.

### 6. Payment outcome unknown

Show: “Payment status unclear. Money may have been debited. Check your bank's official transaction history before paying again.” Preserve the attempt and reservation. No automatic retry or timer-driven resolution. User notes or manually entered references remain unverified and cannot unlock resubmission.

### 7. USSD interrupted

Record the interruption without raw screen text. If external interaction may have started, commit UNKNOWN. Do not redial automatically. Show the same uncertainty warning, even if the carrier dialog disappeared or reported an ambiguous error.

### 8. App process killed

The user may see no immediate response. The journal, not a running screen or service, preserves the attempt and possible-submission boundary. On restart apply flow 9; no background assumption of failure or success is allowed.

### 9. Restart with unresolved attempt

Recover before enabling another payment. An INITIATED attempt may remain ready only when durable evidence proves external interaction was not armed. Interrupted PROCESSING becomes UNKNOWN. Restored records are quarantined. Show unresolved details and independent-verification guidance; never reopen the dialer automatically. Durable SUCCESS remains SUCCESS when a subsequent verification request is unavailable.

### 10. Another payment while an earlier attempt is unresolved

Block new payment submission while INITIATED, PROCESSING, or UNKNOWN holds the durable reservation, including a new payee, amount, or SIM. Explain the earlier unresolved attempt and provide its details. This conservative Phase 1 restriction cannot prevent the user paying independently through another application. A user assertion or elapsed time does not release the reservation. Proven cancellation or approved definitive resolution is required; no manual override is in Phase 1 scope.

### 11. Verification unavailable

Show: “V4SH cannot verify this payment with your bank. Check your bank's official transaction history.” Do not show a fake checking animation or imply a bank query. Retain UNKNOWN after possible interaction. If a future authorized adapter becomes available, show checking only during a real attempt-specific query, without altering the existing financial state merely to animate progress.

### 12. Late provider response

Validate authenticity, freshness/replay rules, attempt correlation, and finality. Qualifying evidence can resolve UNKNOWN through the central state machine. Untrusted or unrelated responses leave the state unchanged. Duplicate evidence has no second effect. Contradictions against terminal records open an audited investigation; they do not silently rewrite history. If a result is already durably committed, reopening the app displays that result without re-execution.

## UNRESOLVED QUESTIONS

VERIFICATION SOURCE: UNRESOLVED

No late-response delivery channel or provider query interface is assumed. Support ownership, long-lived UNKNOWN handling, compatibility, accessibility consent, and localized wording need approval. Until then SUCCESS AUTHORIZATION = BLOCKED; UNKNOWN = SAFE FALLBACK after possible submission. This conservative design can leave payment execution blocked indefinitely; product approval must accept that limitation before Phase 2.

Implementation status: Documentation only. No application source code was modified.
