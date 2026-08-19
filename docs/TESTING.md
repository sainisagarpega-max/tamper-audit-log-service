# Testing and Validation

## Objective

The test suite validates API behavior, deterministic hash chaining, authorized lifecycle operations, and detection of unauthorized database changes. Tests use the local H2 profile and the same Flyway migrations and JPA services used by the application.

## Coverage

| Requirement | Test coverage |
| --- | --- |
| Write API | Authenticated writer receives `201`, sequence and hash metadata; invalid input receives `400`; incorrect role receives `403`. |
| Query API | Reader can filter and paginate results; unauthenticated access receives `401`. |
| Verify API | Reader receives intact chain results and details of the first detected violation. |
| Hash chain | Genesis link, sequential links, canonical JSON key ordering, and multi-record verification are checked. |
| Chain head | Deleting the last event or every event is detected by comparing stored records with `chain_state`. |
| Concurrency | Twelve parallel H2 writes must produce unique consecutive sequences, correct predecessor links, and an intact final chain. |
| Retention | Expired records are hidden by default, remain available when archived records are included, retain chain integrity, and are not archived twice. |
| Redaction | A nested sensitive value is replaced, a salted proof is produced, the receipt is anchored by a new event, and verification remains intact. Missing fields and repeated redaction are rejected. |
| Compliance reporting | Allowed and denied account access events are reportable without client data, and report access creates its own audit event. |
| Export verification | Valid and legitimately redacted bundles pass; a modified event or redaction receipt fails even after the outer bundle hash is recomputed. The verification HTTP endpoint is also exercised. |
| JWT audience | The required audience is accepted, an unrelated audience is rejected, and locally issued tokens pass the real decoder with `aud=audit-log-api`. |

## Tampering simulations

Tests bypass the application and execute SQL updates directly to represent unauthorized storage modification:

1. Changing a hashed event field produces `CONTENT_HASH_MISMATCH` at the modified sequence.
2. Replacing a record's previous hash produces `PREVIOUS_HASH_MISMATCH`.
3. Deleting an internal record produces `SEQUENCE_GAP` at the next available sequence.
4. A legitimate redaction is distinguished from tampering because its proof is stored in a receipt and anchored by an appended `PAYLOAD_REDACTED` event.

These tests demonstrate detection, not prevention. Database permissions, immutable backups, external chain-head anchoring, monitoring, and alerting remain production controls.

## Running the suite

```bash
mvn test
```

Validation on 19 August 2026: **29 tests passed, 0 failures, 0 errors, 0 skipped**. PostgreSQL runtime behavior is not exercised by this suite and must not be claimed as tested.
