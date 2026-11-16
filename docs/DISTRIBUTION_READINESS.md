# Distribution and institutional readiness

Status: NOT PRODUCTION READY. Code changes do not constitute institutional authorization or store approval.

## Accessibility

V4SH is a deterministic, user-started cellular menu assistant, not a disability accessibility tool. Its service explicitly declares isAccessibilityTool=false. A separate normal-launch disclosure describes non-password phone-dialog text, menu/payment-detail interaction, local processing, balance presentation and manual PIN boundaries. Affirmative consent is required before session launch or event processing. Declining preserves QR preparation, Receive and local history; withdrawal is available in Payment Readiness. Android service enablement remains a separate user action.

Google Play's [Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) distinguishes deterministic scripts from prohibited autonomous planning and requires disclosure, consent and a declaration/review for this use. This implementation is not proof of acceptance. Owner must submit an accurate declaration, listing and demonstration and obtain review approval before distribution.

## Payments and data

Owner must complete the [financial-features declaration](https://support.google.com/googleplay/android-developer/answer/13849271), privacy policy and Data Safety statements based on the final artifact and all SDK behavior. No declarations were submitted. No bank, PSP, NPCI or carrier authorization has been supplied. NPCI's [UPI participant overview](https://www.npci.org.in/PDF/npci/upi/Product-Booklet.pdf) describes bank authentication and participant roles; it is not an integration contract or permission for V4SH. The appropriate institutional model and carrier/USSD automation terms need documented provider and legal review. No claim of illegal use or approval is made.

An authorized, technically accessible, attempt-correlated confirmation mechanism remains absent. SUCCESS/FAILED reconciliation and new payments behind the existing unresolved guard remain BLOCKED. Screen text, balance movement, manual acknowledgment or clearing local data cannot substitute for reconciliation.

## Qualification and operational ownership

Only the user's Redmi/Airtel balance flow was physically demonstrated on prior builds. Other devices, banks, operators, languages, SIM routing and interrupted-service conditions are not qualified. Readiness uses public permission-minimal device observations and cannot attest bank registration, coverage or carrier support. READ_PHONE_STATE is requested only when the owner opens the SIM chooser; readiness adds no location, SMS or contact permissions.

Before release, owner must establish approved supported combinations, accessible support/escalation procedures, retention/deletion rules that preserve unresolved-payment safety, incident ownership, privacy contact, signed distribution identity and update/recovery procedures. Device transfer and cloud restore need real qualification beyond source exclusions and synthetic fault tests. No signing compromise is asserted without evidence.

## Gap-closure ownership gates — 3 November 2026

ENGINEERING COMPLETE applies only to implemented local controls with passing recorded tests, not the whole payment product. EXTERNAL APPROVAL REQUIRED cannot be satisfied by source code, an APK signature or a user's manual acknowledgment.

| Gate | Engineering evidence / deliverable | Owner-dependent requirement |
| --- | --- | --- |
| Payment authority | AUTHORIZED_CONFIRMATION_CONTRACT.md and bounded primary-source investigation | Provider/bank owner must establish access rights, exact consumer-*99# scope, unique correlation and finality. BLOCKED. |
| Institutional model | Existing consumer assistance rail preserved; no merchant model invented | Institution/legal owner must determine approved operating model, bank/PSP/NPCI roles and carrier automation terms. EXTERNAL APPROVAL REQUIRED. |
| Accessibility/distribution | Optional affirmative disclosure, non-tool metadata, narrow session ownership and truthful Help | Release owner must submit declarations/listing/demo and obtain applicable distribution review; no Play approval claimed. |
| Permissions/SDK privacy | Minimal merged permissions, no INTERNET, factual ENGINEERING_DATA_INVENTORY.md | Privacy owner must review exact SDK metrics/system-service behavior, publish appropriate notices, complete Data Safety/financial features, choose supported regions and legal obligations. No declaration submitted. |
| Retention/deletion/recovery | Unresolved guard, encryption, backup restrictions and explicit all-data-loss limits | Privacy/provider owners must define retention/user-rights and authorized reconciliation/recovery before deletion or key rotation. Do not implement clear-history-as-unlock. |
| Compatibility | COMPATIBILITY_QUALIFICATION.md separates real tests from synthetic policy checks | QA/carrier/bank owners must qualify dual SIM, coverage loss, other banks/carriers/OEMs/languages, real transfer and 16 KB runtime. |
| Support/incident operations | User-controlled note chooser; no support backend/ticket claim | Name a support contact, escalation/complaint path, incident responder, key/exposure response owner and service commitments. Not configured here. |
| Release custody | Signed shrunk/linted artifact and credential-exclusion checks | Release owner must review signing custody and any actual exposure, approved updates and operational monitoring. No compromise presumed. |

Help now states manual PIN entry, QR-name provenance, uncertainty/no retry, SIM-routing limits and note-only sharing. Current local behavior must not be marketed as institution-approved, universally compatible or capable of settling/reconciling payments. See UI_CLAIM_AUDIT.md for reviewed claims. Engineering data inventory is review input, not a fabricated privacy policy.

Release deployment qualification also remains open: debug/release signing identities differ. The current protected real record is in the installed debug app; no destructive uninstall/clear was performed to test the signed release. Release owner must approve a signing/update/data-migration and reconciliation strategy that preserves unresolved protection. Signed-release runtime is NOT TESTED, separately from passing static signature/build/manifest/alignment checks.
