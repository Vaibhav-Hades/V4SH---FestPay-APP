# Authorized payment confirmation: decision and minimum contract

Reviewed: 3 November 2026. This is an engineering entry contract, not an implemented integration or legal/provider approval.

## Five explicit answers

1. **Can current V4SH authorize SUCCESS? No.** PaymentIntegrityPolicy.confirmationAvailable is false, its carrierOutcome returns UNKNOWN, and it rejects transitions to SUCCESS/FAILED. RealUssdPaymentEngine returns UNKNOWN. The journal accepts no provider reference/provenance and no terminal evidence. Accessibility and the UI cannot add authority.
2. **Exact missing dependency:** contractual authorization and an authenticated, technically accessible bank/PSP confirmation channel covering the actual consumer *99# transfer, with a provider-supported unambiguous binding between the immutable V4SH attempt and a specific external transaction. An API key for unrelated merchant orders is insufficient.
3. **Minimum integration:** an authorized read-only confirmation/reconciliation capability for the actual rail/accounts, plus a trusted correlation mechanism established before submission or returned through an authenticated provider channel. If no provider can correlate assisted *99# transactions, the existing rail cannot acquire this capability by adding a status button. Changing to a provider-controlled initiation/order rail is a separate approved product/architecture decision, not an implementation in this task.
4. **Information required:** the evidence fields and finality guarantees below, supplied under documented access rights. Mere matching of amount, recipient and approximate time is not unique correlation.
5. **Changes after approval:** design/verify the real adapter and credential boundary, persist the binding/evidence, add an evidence validator and transactional reconciliation command, extend the schema/state policy and guarded UI, then test adversarial/failure/recovery cases before a controlled authorized payment. Do not flip the current Boolean as a substitute.

## Concrete future architecture (not implemented)

```text
Durable immutable local attempt + frozen request
    -> provider-supported authenticated correlation binding
    -> existing *99# interaction ONLY if provider supports correlating it
    -> separate provider transaction reference
    -> authenticated read-only provider status/reconciliation result
    -> provenance + principal + exact-attempt + finality validation
    -> atomic evidence/event/version/financial-state update
    -> SUCCESS or definitive FAILED

Any missing, conflicting, pending, unverifiable or ambiguous evidence
    -> UNKNOWN / reservation retained / no automatic resubmission
```

Alternative provider-controlled initiation must preserve durable creation/idempotency before its external effect. It requires an explicitly approved change of product scope and authorization; merchant onboarding, a server or a new payment rail must not be silently introduced.

## Required evidence contract

| Field/guarantee | Required interpretation |
| --- | --- |
| Issuer, environment, authorization scope | Authenticated bank/PSP identity; production evidence cannot come from a sandbox; access covers the real payer/transaction. |
| Immutable local attempt binding | Provider-recognized client/idempotency correlation or another documented authenticated one-to-one binding; local UUID alone is not an external reference. Persist before relying on it. |
| Provider transaction reference | Real provider-issued unique identifier with namespace and provenance; UTR/RRN only if genuinely supplied. Never derive it from the local ID. |
| Frozen transaction details | Exact amount in defined minor units, currency, intended destination and authorized payer/account scope; validate against the immutable request. Minimize retained identifiers. |
| Final status semantics | Document which provider state means completed payment and which means definitive failed/non-settled transfer. Authorization, submission, order status, capture and merchant settlement are different concepts; no generic label-to-SUCCESS mapping. |
| Event/read provenance | Authenticated API/verified signed event, issuer/key identity, evidence ID, provider occurrence time, local receipt time, verification method/version and an audit digest/reference. HTTPS alone does not establish authorization or correlation. |
| Freshness/order/replay | Document allowed late events, event sequence/version, replay deduplication and conflict handling. Duplicate events are idempotent; stale/conflicting evidence cannot downgrade terminal truth or unlock a guard. |
| Completeness/failure guarantees | API timeout/not-found is not proof of failure. Provider must explain finality, reversals and reconciliation coverage; unresolved or contradictory states stay UNKNOWN. |

A single merchant order can contain multiple payment attempts. Order-level paid/unpaid is not automatically evidence for one V4SH attempt. Read-only status rechecks may be safely retried under a real contract; payment submission may not be retried merely because verification failed.

## Correlation of the existing real unresolved attempt

It has no authenticated provider binding/reference in V4SH. A future integration cannot retroactively manufacture one. The provider must supply an authorized recovery/reconciliation procedure that uniquely identifies that transaction and establishes finality. If that cannot be established, the attempt remains UNKNOWN and protected. User acknowledgment, screenshots, SMS, balance changes and an unrelated newly-created provider order cannot release it. Do not delete it, reinstall to claim resolution or create a replacement payment for testing.

## Required implementation gate after a real contract exists

- Keep provider credentials out of the APK. A provider-approved credential-bearing service or documented secure client protocol is required; none exists today. Specify trust, access control, retention and incident ownership before implementation.
- The current app has no INTERNET permission or network adapter. An HTTPS-based confirmation channel changes connectivity/privacy/distribution requirements. Preserve offline preparation/USSD and report UNKNOWN while confirmation is unavailable; never promise authoritative online status while fully disconnected without a documented offline evidence channel.
- Add a new reviewed evidence schema and migration that retain old UNKNOWN reservations. Validate evidence before one central reconciliation transaction updates state/event/version and, only under the approved finality policy, reservation. Dirty storage still blocks; restart never resubmits.
- Test wrong attempt/provider/principal/environment/amount/currency/reference, missing or forged evidence, pending/not-found responses, duplicates, reordered/late/conflicting events, invalid signatures/expired keys, crashes around evidence commit, restore, migration and network loss. A Boolean capability flag or successful HTTP response is not an acceptance test.
- Obtain institution/provider authorization and distribution/privacy approval, then perform a controlled real payment with explicit user authorization and manual PIN. No such payment was performed here.

## Bounded investigation conclusion

**NOT FOUND** means no authorized, usable confirmation/correlation mechanism for the current consumer *99# architecture was established. Documented status services exist in other authorized products; it does not mean that financial APIs do not exist. No arbitrary access was attempted, no credentials were requested, and no provider account was created. See PAYMENT_CONFIRMATION_INVESTIGATION.md for reviewed primary sources and scope.
