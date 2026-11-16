# Protected local storage — implementation checkpoint

V4SH retains its SQLite attempt journal. Schema v2 stores authenticated encrypted payloads for attempts, reservations and events; profile and legacy-history preference values use an encrypted envelope. This protects local confidentiality/integrity, not bank authenticity.

## Keys and payloads

- Android Keystore generates a device/app-bound AES-256 key for each store namespace. No exported key bytes, hardcoded keys, network key service or committed secrets are used.
- AES/GCM/NoPadding uses a provider-generated 96-bit nonce and 128-bit authentication tag. A version byte and bounded envelope size are checked before decryption. Namespace/table/opaque-row-key additional data prevents cross-record substitution.
- IDs, creation/event timestamps, names, recipients, amounts, currencies, financial states, operational metadata and reservation contents are encrypted. SQLite row indexes are hashes of random local identifiers; event count and schema structure remain observable metadata.
- An encrypted integrity header covers the encrypted rows. A separate encrypted AtomicFile seal in no-backup storage binds the expected revision/root. Deletion, partial replacement and an old DB snapshot with the current seal fail closed.
- This is not a hardware monotonic counter: an attacker able to roll back all app files and both integrity locations is outside the protection provided. Clearing/uninstalling all application data cannot be made a universal cross-app/bank duplicate guard.

## Writes and failures

SQLite transactions retain create/reservation/event atomicity. A dirty seal is durably written before committing a changed journal. The final seal is written only after SQLite commit. A crash in that interval makes storage explicitly unavailable; no guessed recovery, empty replacement or payment replay occurs. A crash before commit can be rolled back by SQLite; a dirty seal still conservatively blocks execution.

Missing/corrupt keys, authentication failure, unsupported schema/data, dirty/missing seals or unreadable/missing initialized journals block execution and present unavailable history. Previously displayed records can remain visible with an explicit refresh error; they do not authorize new effects. Missing keys are never silently regenerated for existing protected data. No automatic destructive repair exists.

Keys are not tied to a biometric enrollment or a per-use app authentication prompt; carrier PIN authentication remains entirely separate. Hardware backing depends on the device and is not assumed. Device-bound keys are not backed up/exported. Institutional recovery/key-rotation procedures still require a reviewed reconciliation/retention policy; the app does not rotate away unreadable unresolved records.

## Migration

The v1 journal migrates inside SQLite's upgrade transaction, preserving IDs, valid timestamps, details and the active reservation. Unsupported terminal labels become UNKNOWN without gaining provider provenance. Sensitive old columns/tables are replaced with encrypted records; secure_delete and a post-commit VACUUM remove plaintext values from the current logical DB file. Migration remains guarded until completion. This does not promise forensic erasure of flash blocks or historical external copies.

Known profile/legacy-history preference values are validated and moved into an encrypted value with a committed write before the old keys are removed by that same editor transaction. Malformed legacy JSON or unrecoverable fields fail migration without silently discarding the old data. Invalid relative legacy timestamps are explicitly unavailable, not replaced with a fabricated instant. There is no way to retrospectively detect deletion that happened before this protected-store protocol was installed.

## Verification checkpoint

AES-GCM unit tests passed. Six isolated Redmi storage tests passed: v1 migration/identity/reservation/plaintext checks, ciphertext/reservation tampering and stale DB snapshots, missing key/DB/seal and dirty writes, profile migration/key loss, malformed JSON/invalid timestamp, injected key invalidation and concurrent exclusive starts. Keystore key export returned null in the tested configuration. Full-suite and additional process-death tests remain in progress.

Basis: [Android Keystore system](https://developer.android.com/privacy-and-security/keystore). A Keystore-protected local record remains local data and is never authoritative financial evidence.

## Final gap-closure checkpoint — 3 November 2026

The previous six-test statement is historical. Current full suite: 74 JVM tests and 30 Redmi Android tests PASS. Additional negative coverage verifies malformed v1 recipient/unsupported schema preserves attempt/reservation rows while refusing reads/new creation; duplicate events/IDs, stale versions, interrupted writes, missing/invalidated keys and partial rollback remain covered. Actual kill/force-stop/reboot repetition passed. Injected key invalidation is not a physical hardware-invalidation test, and an old database snapshot with a current seal is not proof against rollback of all application files. No storage format, key policy or real unresolved guard was changed in this gap-closure run.
