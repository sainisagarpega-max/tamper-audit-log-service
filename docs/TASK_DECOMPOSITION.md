# Task Decomposition

## 1. Delivery approach

Implementation will proceed in small, reviewable milestones. Scenario A establishes the immutable audit chain first. Scenario B extends that stable foundation with retention, redaction, and export. Scenario C adds the clarified compliance-access use case. Each milestone ends with automated tests and documentation updates before the next begins.

## 2. Scenario A APIs

### 2.1 Write audit event

`POST /api/v1/audit-events`

Purpose: Append one immutable event to the audit chain.

Request fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `eventType` | string | Yes | Describes what happened. |
| `actorId` | string | Yes | User or service that caused the event. |
| `resourceType` | string | Yes | Type of affected resource. |
| `resourceId` | string | Yes | Identifier of the affected resource. |
| `payload` | JSON object | Yes | Structured event details. |
| `occurredAt` | UTC timestamp | No | Caller-provided occurrence time; must not be in the future. |

The server assigns `id`, `sequenceNumber`, and `recordedAt`, links the previous hash, calculates the new content hash, and stores the record in one transaction.

Acceptance criteria:

- A valid authorized request returns HTTP `201 Created`.
- The first event uses the defined genesis previous hash.
- Every later event references the immediately preceding event's content hash.
- Invalid input returns HTTP `400` without storing an event.
- Missing or invalid authentication returns HTTP `401`.
- Insufficient authority returns HTTP `403`.
- No update or delete event endpoint exists.

### 2.2 Query audit events

`GET /api/v1/audit-events`

Purpose: Retrieve audit events using any supported combination of filters.

Query parameters:

| Parameter | Type | Required | Notes |
| --- | --- | --- | --- |
| `actorId` | string | No | Exact actor match. |
| `resourceType` | string | No | Exact resource-type match. |
| `resourceId` | string | No | Exact resource match. |
| `eventType` | string | No | Exact event-type match. |
| `from` | UTC timestamp | No | Inclusive lower time boundary. |
| `to` | UTC timestamp | No | Inclusive upper time boundary. |
| `page` | integer | No | Zero-based page, default `0`. |
| `size` | integer | No | Default `20`, maximum `100`. |

Acceptance criteria:

- Any filter can be used alone or combined with other filters.
- Results are ordered by ascending chain sequence.
- Pagination metadata is returned.
- An invalid range where `from` is after `to` returns HTTP `400`.
- The endpoint requires read authority.

### 2.3 Verify hash chain

`GET /api/v1/audit-events/verify`

Purpose: Walk the chain in sequence order and recompute every hash.

Example intact response:

```json
{
  "intact": true,
  "recordsChecked": 25,
  "firstBrokenSequence": null,
  "violationType": null
}
```

Example broken response:

```json
{
  "intact": false,
  "recordsChecked": 7,
  "firstBrokenSequence": 7,
  "violationType": "CONTENT_HASH_MISMATCH"
}
```

Initial violation types:

- `GENESIS_HASH_MISMATCH`
- `SEQUENCE_GAP`
- `PREVIOUS_HASH_MISMATCH`
- `CONTENT_HASH_MISMATCH`
- `CHAIN_HEAD_MISMATCH`

Acceptance criteria:

- An empty chain is reported as intact with zero records checked.
- An unchanged chain is reported as intact.
- Direct modification of hashed event content is detected.
- A changed predecessor link or sequence gap is detected.
- A deleted tail event or completely deleted event table is detected by comparison with `chain_state`.
- The first inconsistent sequence and violation type are reported.
- The endpoint performs no repair or data mutation.
- The endpoint requires read or administrative authority.

## 3. Data model

### 3.1 Audit event record

| Column | Type | Purpose |
| --- | --- | --- |
| `id` | bigint | Internal database identity. |
| `sequence_number` | bigint, unique | Stable global chain position. |
| `event_type` | varchar(100) | Event classification. |
| `actor_id` | varchar(100) | Responsible user or service. |
| `resource_type` | varchar(100) | Resource classification. |
| `resource_id` | varchar(150) | Affected resource identifier. |
| `payload` | text/JSON | Canonical structured event details. |
| `occurred_at` | timestamp with time zone, nullable | Optional caller occurrence time. |
| `recorded_at` | timestamp with time zone | Server-controlled ingestion time. |
| `previous_hash` | char(64) | SHA-256 hash of the preceding event. |
| `content_hash` | char(64) | SHA-256 hash calculated for this event. |
| `hash_version` | integer | Canonical format and hashing-rule version. |

Required indexes:

- Unique index on `sequence_number`.
- Index on `actor_id`.
- Composite index on `resource_type, resource_id`.
- Index on `event_type`.
- Index on `recorded_at`.

### 3.2 Hash-chain rules

1. Sort and normalize the payload into a deterministic JSON representation.
2. Build a versioned canonical representation using a fixed field order and UTF-8 encoding.
3. Include sequence, event fields, both timestamps, previous hash, and hash version.
4. Use 64 lowercase zero characters as the first event's previous hash.
5. Calculate `SHA-256(canonical event bytes)` and store it as lowercase hexadecimal.
6. Never update a stored event through the application.

The hash calculation must be isolated in a dedicated component so creation and verification use exactly the same implementation.

### 3.3 Chain-head state

A small single-row `chain_state` table will hold the latest sequence and hash. The write transaction locks this row, allocates the next sequence, stores the event, and advances the chain head atomically. This replaces JVM-only synchronization and remains correct when multiple application instances write concurrently.

| Column | Type | Purpose |
| --- | --- | --- |
| `chain_name` | varchar, primary key | Chain identifier; `GLOBAL` for the prototype. |
| `last_sequence` | bigint | Latest committed chain position. |
| `last_hash` | char(64) | Latest committed content hash. |

## 4. Dependencies

### Application dependencies

- Spring Web MVC: REST controllers and HTTP responses.
- Jakarta Validation: request validation.
- Spring Data JPA: persistence and query specifications.
- Spring Security OAuth 2.0 Resource Server: JWT validation and authorization.
- Jackson 3: JSON request handling and canonical payload processing.
- Java `MessageDigest`: SHA-256 calculation.
- Springdoc OpenAPI: Swagger/OpenAPI documentation.
- Spring Boot Actuator: health and basic operational endpoints.

### Storage dependencies

- H2: fast local development and basic automated tests.
- PostgreSQL: production database and concurrency-sensitive integration tests.
- Flyway: versioned database schema and index creation.

### Cross-cutting dependencies

- Pagination depends on deterministic sequence ordering and database indexes.
- Verification depends on canonical hashing being completed and tested first.
- Concurrent writes depend on the chain-state row and database locking.
- JWT authorization depends on agreed writer, reader, and administrator roles.
- Swagger documentation depends on finalized DTOs and error responses.
- Scenario B retention and redaction depend on a stable, versioned Scenario A hash format.

## 5. Logical implementation sequence

### Milestone 1 - Stabilize project foundation

1. Add Flyway dependencies.
2. Replace Hibernate automatic schema updates with Flyway migrations.
3. Create the `audit_events` and `chain_state` tables and indexes.
4. Add a consistent API error response handler.
5. Confirm application startup on H2 and PostgreSQL.

Quality gate: schema migration tests and application-context test pass.

### Milestone 2 - Implement deterministic hashing

1. Add `hashVersion` to the entity and responses.
2. Create a dedicated canonicalization and hashing component.
3. Sort nested payload object keys deterministically.
4. Define null, timestamp, delimiter, encoding, and genesis rules.
5. Add known-input/known-hash unit tests.

Quality gate: identical logical events produce identical canonical bytes; changing any hashed field changes the hash.

### Milestone 3 - Complete the Write API

1. Add the chain-state entity and repository.
2. Lock chain state within the write transaction.
3. Allocate the sequence, calculate the event hash, persist the event, and update chain state.
4. Enforce the writer authority.
5. Add controller, validation, service, repository, and concurrency tests.
6. Document the endpoint in OpenAPI.

Quality gate: sequential and concurrent writes form one valid linear chain.

### Milestone 4 - Complete the Query API

1. Add optional filter parameters to the request contract.
2. Implement composable JPA specifications.
3. Enforce range validation, bounded page size, and ascending sequence order.
4. Enforce the reader authority.
5. Add single-filter, combined-filter, time-range, empty-result, and pagination tests.
6. Update OpenAPI examples.

Quality gate: every supported filter combination returns correct deterministic results.

### Milestone 5 - Implement the Verify API

1. Define verification response and violation enum DTOs.
2. Stream or page through records in ascending sequence order.
3. Validate genesis, sequence continuity, previous link, and recomputed content hash.
4. Stop at and report the first inconsistency.
5. Add intact, empty, content-tampered, link-tampered, and sequence-gap tests.
6. Add a test-only direct-database tampering fixture.
7. Document verification behavior in OpenAPI.

Quality gate: the assignment's write-query-verify-tamper-verify demonstration passes end to end.

### Milestone 6 - Scenario B extensions

1. Implement archive records and checkpoint manifests.
2. Update verification to traverse archives or validate checkpoints.
3. Implement configured structured redaction and immutable redaction receipts.
4. Implement actor/resource export bundles and an independent verifier.
5. Add retention, redaction, export, and tamper tests.

Quality gate: legitimate archiving/redaction preserves verification, while unauthorized modification remains detectable.

### Milestone 7 - Scenario C compliance access

1. Implement the clarified client-account-access event schema.
2. Record allowed and denied access attempts without client-data values.
3. Add compliance query/export behavior within the agreed prototype boundary.
4. Audit compliance searches and exports.
5. Document assumptions and excluded access channels.

Quality gate: compliance users can reconstruct who accessed which account, when, why, by which channel, and with what outcome.

### Milestone 8 - Final validation and delivery

1. Run unit, H2 integration, security, and concurrency tests; perform PostgreSQL validation in the deployment environment.
2. Run static analysis and dependency/security checks.
3. Verify local setup from a clean checkout.
4. Complete architecture, API, testing, limitation, risk, and trade-off documentation.
5. Update the AI usage log with accepted, modified, and rejected assistance.
6. Complete the final engineering summary and submission date in `ATTESTATION.md`.

Quality gate: all required artifacts are present, the end-to-end demo is repeatable, and the engineer can explain each decision.

## 6. Immediate next task

Start Milestone 1 by introducing Flyway migrations and a database-backed chain head. Do not extend the public API further until schema creation and deterministic hashing are covered by tests.
