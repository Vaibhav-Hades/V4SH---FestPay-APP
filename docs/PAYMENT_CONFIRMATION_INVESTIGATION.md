# Payment confirmation investigation — 3 November 2026

Scope: current V4SH *99# assistance, not a new merchant/PSP product.

| Mechanism | Finding | Decision |
| --- | --- | --- |
| Android USSD API | sendUssdRequest/UssdResponseCallback document USSD request execution and a network response, not authenticated, attempt-correlated UPI settlement. | Transport observation only. |
| Carrier dialog / Accessibility | No authenticated external reference tied to the local attempt UUID is available in the current implementation. | Observation/interaction only. |
| *99# service/history | No application-accessible authorized status/correlation contract was established. NPCI's current product page required JavaScript and exposed no readable API contract in this investigation. | No adapter established; this is not a claim that no institutional mechanism could exist. |
| Generic UPI intent / Google Pay | Official merchant guidance requires checking with a PSP/payment aggregator even after Submitted/Succeeded. Merchant interfaces do not authorize arbitrary consumer *99# attempts. | Do not substitute a new product or treat an intent response as settlement. |
| Authorized bank/provider API | User confirms none is available; no authorized adapter exists in the repository. | BLOCKED — external dependency required. |
| SMS, balance change, other bank apps, timers, user notes | No authorized transaction-correlated finality contract. | Rejected as verification; no scraping or secret interception. |

Authoritative confirmation mechanism: NOT FOUND for the current integration.
Transaction correlation: UNAVAILABLE.
Production SUCCESS/FAILED authorization: BLOCKED.

Possible submissions remain UNKNOWN and reserved. A future integration must provide authorization, authenticated source identity, separate external reference, correlation to frozen attempt details, finality semantics and replay protection. No provider reference is currently available. Local encryption cannot authenticate bank facts.

Sources reviewed: [Android TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager), [Android UssdResponseCallback](https://developer.android.com/reference/android/telephony/TelephonyManager.UssdResponseCallback), [Google Pay Android merchant guidance](https://developers.google.com/pay/india/api/android/in-app-payments), [NPCI *99# overview](https://www.npci.org.in/what-we-do/99/product-overview). The Google Pay guidance concerns its merchant integration, not authorization for V4SH.

## Final gap-closure investigation — 3 November 2026

Bounded additional primary-source review found real documented status APIs, but no authorized way for this product to correlate/query its arbitrary consumer *99# attempt. This refines the negative finding; it does not imply that bank/payment APIs do not exist.

| Documented category | Evidence | Applicability to current V4SH |
| --- | --- | --- |
| Merchant payment lookup | [Razorpay Fetch a Payment With ID](https://razorpay.com/docs/api/payments/fetch-with-id/) requires credentials and an existing provider payment ID; returns payment/order/status details. | Current V4SH has no merchant integration or provider ID. No documented arbitrary consumer-*99# lookup established. |
| Merchant order reconciliation | [Cashfree Get Payments for an Order](https://www.cashfree.com/docs/api-reference/payments/latest/payments/get-payments-for-an-order) uses client credentials and order ID, with separate payment identifiers/statuses. | A provider order can have multiple attempts. An unrelated new order cannot correlate or resolve the existing real attempt. |
| Bank corporate payment/status capability | [ICICI Corporate Payments API overview](https://www.icici.bank.in/business-banking/cms/corporate-api-suite/payments-api) advertises UPI-capable corporate payments and status inquiry. | A genuine authorized category, not a public access/correlation contract for V4SH consumer *99# traffic. Onboarding, scope, finality and correlation details must come from the institution. |
| Public Android network APIs | Re-read [TelephonyManager USSD contract](https://developer.android.com/reference/android/telephony/TelephonyManager#sendUssdRequest(java.lang.String,%20android.telephony.TelephonyManager.UssdResponseCallback,%20android.os.Handler)). | Response transport does not supply authenticated bank finality or an attempt binding. No private API/reflection/root workaround investigated. |

No credential acquisition, API calls against real bank/payment data, account onboarding or payment submission occurred. No usable authorized provider integration is configured in source. Search stops at this documented boundary rather than guessing private endpoints.

Confirmation mechanism: NOT FOUND for the existing integration. Attempt correlation: UNAVAILABLE. SUCCESS authorization: BLOCKED. FAILED authorization: BLOCKED. Existing unresolved attempt: PROTECTED.

See AUTHORIZED_CONFIRMATION_CONTRACT.md for the five explicit product answers, evidence contract, concrete future architecture, old-attempt recovery limits and implementation gate. Existing data cannot be retroactively assigned a fabricated provider reference. No production adapter, backend, network permission or financial authority was added.
