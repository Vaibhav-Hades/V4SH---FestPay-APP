# V4SH offline feature status — updated 3 November 2026

Offline means mobile data and Wi-Fi can be OFF while cellular signal remains available. Banking operations still require an eligible bank-registered SIM, permitted/supported USSD service, and working Accessibility/Phone permission. Airplane-mode banking is not supported.

| Feature | Current implementation | Verification |
| --- | --- | --- |
| Check Balance | Cellular ACTION_CALL, root option 3, manual carrier PIN, local static-label parser and display | User confirmed correct display with data and Wi-Fi OFF on Redmi/Airtel. Later shared-state changes still require regression. |
| QR scanning/preparation | Bundled ML Kit; strict local URI and amount validation; review | User confirmed recipient extraction and correct Review details with data/Wi-Fi off; carrier payment execution remains unverified. |
| Manual recipient preparation | Validated UPI ID entry, exact amount and review | User confirmed correct manual UPI ID and amount on Review with data/Wi-Fi off on 3 November. Mobile-number entry was replaced at the user's request; no phone-to-account lookup is available. |
| Payment initiation | Real cellular adapter; durable attempt before launch; frozen request transported to service | User confirmed Review detects an existing unresolved attempt and blocks Pay. Further live navigation is blocked pending approved reconciliation; real banking navigation is not certified. Never auto-enter PIN. |
| Payment confirmation | No authorized verification source | BLOCKED; carrier text cannot authorize SUCCESS. UNKNOWN remains reserved and cannot be retried automatically. |
| Receive | Real QR encoding from configured UPI ID, entirely local | Round-trip test passed; user reports friend's payment app scanned it and resolved the user's bank account. No incoming transfer tested. |
| Local history/details | SQLite attempts, resume/manual refresh, explicit read errors, persisted timestamps and selected attempt ID | User confirmed the earlier record and details appear on 3 November. Two device journal tests, one isolated Redmi history-refresh/read-failure test and 61 JVM tests passed. Blocked pre-initiation taps create no financial rows. Not a bank statement. |
| Carrier transaction import | Not implemented without validated accessible response | Deferred; no fabricated records. |
| Profile/help/navigation | Validated committed profile saves, actual permission/Accessibility actions, working Help links, user-controlled support-note sharing | User confirmed readiness/settings, repeat offline scanning, Help and share-chooser navigation. Final pass: 64 JVM and 9 Redmi tests, debug/release builds passed. No separate biometric app lock or support backend is implemented. |

## Test results

- 56 JVM tests passed.
- Debug and release APK builds passed.
- Two isolated journal instrumentation tests passed on the Redmi through ADB; Gradle's offline device-test runner required an uncached host test dependency, so it was not used successfully.
- Release APK checked: no LocalMockPaymentEngine class string in DEX; no INTERNET permission in merged manifest.
- No PIN, balance value, bank reference, or recipient data was captured for the tests.

Latest QR follow-up: 57 JVM tests passed after format compatibility/diagnostic changes. The bundled scanner decoded a synthetic UPI QR in an additional Redmi instrumentation test with INTERNET permission denied. The user corrected an earlier error report and confirmed the camera flow extracted the recipient name and UPI ID. This confirms recipient extraction; offline Review accuracy remains pending. The displayed name is supplied by the QR and is not an authenticated account lookup.

## Operating limitations

Android may require re-enabling Accessibility after replacing the APK or stopping the app. The app now reports an unavailable service instead of pretending menu automation is active. A user-enabled settings switch alone does not prove the service is bound.

An unresolved payment blocks another submission indefinitely unless an approved reconciliation process resolves it. Do not delete app data or reinstall to bypass that reservation; local absence of history does not establish bank non-execution. No automatic retry is implemented.

Latest payment checkpoint: the user clarified that Pay was rejected before launch with "Another cellular operation is active." Removed a duplicate balance-running flag tied to screen composition; preflight rejection now stays on Review and is distinct from an initiated UNKNOWN attempt. Service lifecycle cleanup and manual UPI ID entry were updated. Live QR-to-carrier navigation remains unverified.

Subsequent screenshots showed the older combined storage/unresolved guard message. After installing the diagnostic update (59 JVM tests passed), the user confirmed Review reports an earlier unresolved attempt without tapping Pay. The current blocker is therefore the durable reservation. New live payment execution remains blocked until approved reconciliation is available; clearing data or replacing history is not a resolution. The QR verification claim was corrected and the status button now permits text-height growth.

Secure-storage encryption, complete process-death/restore tests, SIM selection, provider authorization, payment confirmation, and broad device qualification remain release blockers. This status report does not certify the application as production-ready or establish that all features have passed physical tests.
