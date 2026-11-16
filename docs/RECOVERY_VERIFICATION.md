# Recovery verification — 3 November 2026

All fixtures used synthetic stores. No payment adapter, dialer, PIN interaction or real user journal was used by the recovery harness. Debug fixture components are excluded from release.

| Case | Mechanism | Result |
| --- | --- | --- |
| Before mutation | Actual app-process SIGKILL from debug instrumentation; new-process verifier | TEST VERIFIED: UNKNOWN, reservation retained, second create rejected. |
| After SQL mutation, before dirty seal | Actual process kill, SQLite hot-journal recovery, new-process verifier | TEST VERIFIED: UNKNOWN, reservation retained, no replay. |
| Dirty seal before SQL commit | Actual process kill, new-process verifier | TEST VERIFIED: explicitly unavailable; reservation row retained, execution blocked. |
| SQL committed before final seal | Actual process kill, new-process verifier | TEST VERIFIED: explicitly unavailable; reservation row retained, execution blocked. |
| Final seal committed | Actual process kill, new-process verifier | TEST VERIFIED: UNKNOWN after recovery, reservation retained, no replay. |
| PROCESSING + force-stop | Persist fixture; adb force-stop; new process | TEST VERIFIED: UNKNOWN and reserved. |
| UNKNOWN + force-stop | Persist fixture; adb force-stop; new process | TEST VERIFIED: UNKNOWN and reserved. |
| Device reboot + manual unlock | Persist synthetic UNKNOWN; actual Redmi reboot; user confirmed unlock; new-process verifier | VERIFIED device operation / TEST VERIFIED stored result: UNKNOWN, reservation retained, no replay. |
| Activity recreation | Synthetic debug Activity + ActivityScenario.recreate | TEST VERIFIED: UNKNOWN, selected identity and one attempt retained. |
| Configuration change | Requested and observed landscape configuration | TEST VERIFIED: UNKNOWN and reservation retained. |
| Cold activity restart | Close/relaunch synthetic activity with a new ViewModel | TEST VERIFIED: UNKNOWN and reservation retained. |
| Interrupted-write exception matrix | Exceptions at all five transaction boundaries | TEST VERIFIED: UNKNOWN where readable, otherwise explicit unavailable state; no second creation. |
| Stale record version | Mismatched expected-version command | TEST VERIFIED: rejected without state change or reservation release. |
| Real Android low-memory killer | Not induced by exhausting device memory or killing unrelated apps | NOT TESTED. Abrupt process-loss tests cover loss of volatile V4SH state, not LMK scheduling or every OEM memory policy. |
| Cloud/device-transfer restore | Manifest/XML exclusions plus missing-key/seal/old-snapshot tests | TEST VERIFIED exclusions/fault behavior; actual cloud/device-transfer restore not performed. |

Unavailable is not a claim of financial failure. Dirty/missing/corrupt storage is never reset automatically or interpreted as permission to retry. SQLite rollback and encryption integrity are distinct from authoritative bank reconciliation, which remains unavailable.

Reproduction: tools/verify_recovery.ps1 uses the debug-only RecoveryProbeInstrumentation. CrashAndRestart covers five actual kills and two force-stops; SeedReboot/VerifyReboot require a manually unlocked device and retain only a synthetic host fixture under app/build between commands. Regular JUnit tests cover the exception matrix and ActivityScenario lifecycle cases. All commands preserve real app data and contain no automatic payment call.

## Fresh gap-closure repetition — 3 November 2026

The updated build passed all five actual synthetic process-kill boundaries and both force-stop cases again. A fresh physical Redmi reboot was requested after the user confirmed readiness; after manual unlock, VerifyReboot passed: UNKNOWN, reservation retained, no resubmission. The fixture was cleaned by the synthetic harness. No real financial record, dialer or payment submission was used. The current complete Android suite also passed the recreation/observed rotation/cold-start and boundary tests (30/30 overall). Actual LMK policy and cloud/device transfer remain NOT TESTED.
