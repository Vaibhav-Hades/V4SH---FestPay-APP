# Beneficiary-name access investigation

3 November 2026. Scope: current QR/manual-VPA preparation over consumer *99#, with no sponsor/provider integration or new live payment. This is an engineering investigation, not institutional authorization.

## Eight requested answers

| Question | Finding / exact limit |
| --- | --- |
| 1. Who has access to Validate Address? | NPCI's beneficiary-name circular addresses UPI ecosystem members. Oracle's PSP implementation documentation describes ReqValAdd as a PSP-to-UPI-switch service. No anonymous public lookup entitlement for arbitrary standalone apps was established. |
| 2. Does current V4SH qualify? | No demonstrated qualification/access: this app has no sponsor-bank/TPAP/provider contract, credentials, certified API channel or authorized lookup adapter. Being an Android app with Accessibility does not establish that access. |
| 3. PSP bank / TPAP relationship? | NPCI's public FAQ directs a TPAP applicant to a sponsor bank for onboarding. Direct participation or a separately authorized downstream service must establish the appropriate access/scope. No particular paid intermediary is presumed authorized. |
| 4. Required request fields? | The current authoritative NPCI production wire schema was NOT ESTABLISHED from the reviewed public material. Obtain the current versioned ReqValAdd/provider schema from the sponsor. Do not implement from third-party mirrors of 2016 specifications. The app's internal query has a unique local lookup ID and exact VPA; it is not an NPCI API payload. |
| 5. Response fields? | Current NPCI field names/mandatory cardinalities/signatures were NOT ESTABLISHED. A real adapter must authenticate its provider and extract the actual bank-record name with exact VPA/request correlation, provenance, status and time. Internal BeneficiaryReply is a typed acceptance boundary, not a claim about NPCI's wire response. |
| 6. Can it supply the ultimate bank-record name? | NPCI's April 2025 circular explicitly identifies Validate Address as the source of the bank/CBS beneficiary name for P2P/P2PM pre-transaction display. This documents the capability; no real name was fetched for V4SH and verified display remains BLOCKED. |
| 7. Contract/certification/security requirements? | Sponsor/provider must establish onboarding, current NPCI/sponsor certification and security requirements, authentication/signature protocol, permitted use/data retention and authority for the specific API. None were supplied or fulfilled by this task. A checklist from an unrelated merchant product is not certification. |
| 8. Legitimate independent-app route? | Apply through a sponsor-bank/authorized integration relationship and obtain the actual documented contract, scope and approval. A separately authorized narrow lookup service could only be evaluated after proving authority/provenance/legal basis. No vendor selected, account created, outreach sent or credentials invented. |

## Primary evidence and applicability

- [NPCI OC-101A, 24 April 2025](https://www.npci.org.in/uploads/UPI_OC_No_101_A_FY_2025_26_Strengthening_beneficiary_name_verification_and_display_during_UPI_transactions_eb7bd7ed72.pdf) requires the bank/CBS name obtained through Validate Address for P2P/P2PM details and excludes QR/user-derived substitutes. It also restricts beneficiary-name editing. The current payment UI therefore drops QR pn as a displayed identity. This change is not a declaration of institutional compliance or readiness.
- [NPCI UPI FAQ](https://www.npci.org.in/what-we-do/upi/faqs) identifies sponsor-bank onboarding for TPAPs. No current V4SH relationship is established.
- [Oracle PSP non-financial APIs](https://docs.oracle.com/en/industries/financial-services/banking-payments/14.8.3.0.0/inuug/psp-upi-switch-non-financial-apis.html) documents ReqValAdd in a PSP switch. [Oracle's delegated-flow description](https://docs.oracle.com/en/industries/financial-services/banking-payments/14.9.0.0.0/inuug/upi-circle-full-delegation-apis.html) shows request/response routing via PSP/NPCI. These are vendor implementation references, not a license, full current NPCI schema or authority for this app.
- [Official Paytm merchant Validate VPA](https://www.paytmpayments.com/docs/api/validate-vpa/) is a different scoped product. It documents merchant ID, authorization token/type and conditional order/reference context, with VPA input and validity/result metadata in the response. Its reviewed response does not establish a bank-beneficiary-name field. A validity Boolean cannot supply CBS identity. V4SH has no credentials/permission for it and does not call it.

No unofficial public lookup site, bank-app scraping, SMS/OTP interception or private Android API was used. No arbitrary endpoint was probed. Additional searches stop at the access/schema boundary rather than inventing a production interface.

## Carrier recipient-name investigation

Carrier response: **NOT TESTED** in this run. The existing real UNKNOWN reservation is protected, and the user explicitly requires that it legitimately permit a test before *99# -> 1 -> 3 -> VPA navigation. It does not. No standalone/manual carrier launch was used to bypass that condition, and no new payment attempt or PIN screen was opened.

Therefore the name's location (before amount versus carrier Review), actual CBS provenance, Accessibility exposure and per-session correlation have not been established. Earlier payment-navigation reports were inconsistent and are not promoted to a beneficiary-name PASS. Existing service ownership/source guards are interaction controls, not a Validate Address attestation. Even a future observed carrier name must not be labeled VERIFIED without the relevant authorized name-source/provenance contract.

## Implemented boundary

PaymentRecipient, BeneficiaryNameResolver and PaymentPreparation are shared by QR and manual entry. Current production resolver has no provider and immediately returns UNAVAILABLE for valid syntax, or INVALID_UPI_ID for invalid syntax. No fake PENDING timer or network error is generated when there is no source.

An authorized-adapter interface defines a future trust boundary; it has no production implementation, endpoint, credential or runtime source toggle. Synthetic test adapters live only in src/test. Acceptance checks reject missing/invalid name/provenance/time, different VPA/request and stale replies. A generation ticket prevents old responses overwriting a new recipient, including reuse of the same VPA. PENDING/ERROR are implemented for a genuinely connected source and tested synthetically, not presented as live integration.

Current VERIFIED identity: BLOCKED. Current financial SUCCESS/FAILED: independently BLOCKED. Beneficiary verification is never passed to PaymentIntegrityPolicy as settlement evidence. See AUTHORIZED_CONFIRMATION_CONTRACT.md for the distinct financial integration requirements.
