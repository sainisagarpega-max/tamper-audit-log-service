# Requirements Baseline

## 1. Assignment summary

Build a production-minded, tamper-evident audit log service and preserve evidence of the engineering process used to create it. The service must accept immutable audit events, query them through filters and pagination, protect their integrity with a cryptographic hash chain, and verify that chain after deliberate database tampering.

Extend the core service with retention, privacy-preserving structured redaction, and independently verifiable exports. Turn the ambiguous request "Regulators need to be able to audit access to client account data" into a concrete, defensible design and implementation scope.

AI should assist throughout the work, but the engineer remains responsible for design choices, correctness, security, validation, and the ability to explain or change the system during a live review.

## 2. Confirmed scope

### 2.1 Scenario A - Core audit log service

The authenticated write API shall accept, at minimum:

- `eventType`: what happened.
- `actorId`: the user or service responsible for the event.
- `resourceType`: the kind of resource affected.
- `resourceId`: the specific resource affected.
- `payload`: structured event-specific JSON data.
- `timestamp`: the time at which the event occurred.

The system shall:

- Persist audit events as append-only records and expose no public update or delete endpoint.
- Compute a deterministic SHA-256 content hash from the canonical event representation.
- Store the immediately preceding record's hash, using a documented genesis value for the first record.
- Query by any combination of actor, resource type, resource ID, event type, and time range.
- Paginate deterministic query results.
- Expose a full-chain verification endpoint.
- Report whether the chain is intact and, when broken, identify the first inconsistent record and violation type.
- Compare the verified final event sequence/hash with persisted `chain_state` so tail or complete-chain deletion is detected.
- Detect a modification performed directly in the database.

### 2.2 Scenario B - Retention, redaction, and export

The system shall:

- Apply a configurable retention period.
- Archive records after the retention period while preserving enough evidence to verify the chain without false positives.
- Redact approved sensitive fields within `payload` without destroying proof of the original record.
- Export all records for a given actor or resource ID as a self-contained bundle.
- Include enough metadata for an independent recipient to verify the exported records.

### 2.3 Scenario C - Compliance reporting

The clarified requirement is:

> Authorized compliance users must be able to search and export an immutable history of every read of client account data, showing who accessed which account, when, for what declared purpose, through which channel, and whether access was allowed or denied, without exposing client data values in the audit payload.

The prototype shall cover REST API access mediated by this application. Direct database access, third-party systems, UI screen-view telemetry, and organization-wide regulatory report formats are outside scope and shall be documented as limitations.

### 2.4 Engineering-process deliverables

The repository shall include runnable source and setup instructions; architecture, schema, API, and trade-off documentation; evidence for Scenarios A, B, and C; unit and integration tests; testing scope and limitations; an honest AI usage log; and a final engineering summary.

## 3. Technology decisions

- Runtime: Java 21.
- Framework: Spring Boot 4.1.0.
- Build: Maven.
- API: Spring MVC REST controllers.
- Persistence: Spring Data JPA with Flyway migrations.
- Local/test database: H2, while avoiding H2-specific SQL.
- Production database: PostgreSQL.
- Authentication: Spring Security with short-lived signed JWT access tokens.
- Authorization: `AUDIT_WRITER`, `AUDIT_READER`, and `AUDIT_ADMIN` roles or equivalent authorities.
- API documentation: OpenAPI 3 with Swagger UI and a documented JWT bearer scheme.
- Integrity: SHA-256 over a versioned canonical byte representation.
- Time: UTC `Instant`, serialized as ISO-8601.

## 4. Ambiguities and clarified decisions

### Timestamp authority

Ambiguity: The assignment permits caller-supplied or server-assigned time.

Decision: The server assigns authoritative `recordedAt` in UTC. A caller may optionally supply `occurredAt`, which is validated and included in the content hash but is not trusted as ingestion time.

### Chain scope and concurrency

Ambiguity: A global or partitioned chain is not specified.

Decision: Use one global chain ordered by a database-assigned monotonic sequence. The write transaction shall lock the chain head so concurrent writes cannot create forks. Per-tenant chains are deferred.

### Canonical hashing

Ambiguity: JSON ordering, whitespace, nulls, number representation, encoding, and time formatting can alter a hash.

Decision: Hash a versioned canonical representation with fixed field order, normalized UTC time, deterministic payload-key ordering, explicit null handling, length-delimited values, and UTF-8 encoding. Store `hashVersion` on every record.

### Genesis value

Decision: Use 64 lowercase zero characters as the first record's `previousHash` for hash version 1.

### Query ordering and pagination

Decision: Return ascending chain sequence by default. Use bounded page/size pagination for the prototype. Cursor pagination is preferred for a high-volume production system.

### Retention

Ambiguity: Archive location, retained proof, restoration, and the retention clock are unspecified.

Decision: Archive rather than erase. Retain immutable checkpoint manifests containing the archived sequence range, boundary hashes, record count, archive identifier, creation time, and manifest hash. Full verification reads active and archived records; boundary verification may use checkpoints.

Limitation: The prototype may use an archive table behind an archive interface; production should use independent immutable storage.

### Structured redaction

Ambiguity: Redactable fields, authorization, reversible versus irreversible masking, and legal standards are unspecified.

Decision: Use crypto-shredding for configured JSON paths. Encrypt sensitive values with per-value data keys and hash the ciphertext plus metadata in the original immutable event. Redaction destroys the wrapped key and appends an immutable receipt containing the target, JSON path, reason, actor, and time. Reads return a redaction marker; the original audit row and hash never change.

Trade-off: Plaintext recovery becomes infeasible while the hash chain remains valid, but legal acceptance and key-management controls require security and compliance review.

### Export proof

Ambiguity: A filtered subset of a global chain is non-contiguous.

Decision: Include matching records, original sequences and hashes, boundary/checkpoint evidence, algorithm and format versions, genesis definition, filter criteria, bundle hash/signature, and verification instructions. This proves included records are unchanged. Proving that no matching record was omitted requires a trusted service signature; this limitation shall be explicit.

### Direct database tampering

Decision: Production application credentials cannot update or delete audit rows. A test-only administrative connection or fixture performs the assignment's deliberate modification to prove detection.

### JWT lifecycle

Ambiguity: JWT is an added design choice; issuer, audience, claims, keys, and token issuance are not specified.

Decision: The application acts as an OAuth 2.0 resource server and validates issuer, audience, signature, expiry, and roles. Local development may have a clearly marked test-token facility. Production tokens come from an external identity provider; signing material is never committed.

### Compliance access scope

Ambiguity: "Client account data," "access," regulators, reporting periods, jurisdictions, and formats are undefined.

Decision: Treat application-mediated read attempts as access, including allowed and denied outcomes. Capture account ID, actor, purpose, channel, outcome, correlation ID, and time, but not returned account data. Restrict queries/exports and audit those actions themselves.

## 5. Non-functional requirements

- Return consistent problem-details errors without leaking sensitive data.
- Enforce least privilege on every protected endpoint.
- Atomically lock the chain head, allocate a sequence, hash and store the event, and advance the head.
- Prevent chain forks and duplicate sequences under concurrent writes.
- Use the same Flyway migrations for H2 and PostgreSQL and run PostgreSQL tests for concurrency/database behavior.
- Load secrets from external configuration; never commit them.
- Enable Swagger UI locally; make production exposure configurable and protected.
- Never log payload contents, JWTs, credentials, account numbers, plaintext redacted values, or encryption keys.
- Produce deterministic verification results across restarts and supported environments.
- Include health checks, structured logging, and basic non-sensitive metrics.

## 6. Acceptance criteria

1. An authorized writer can append an event and receives its immutable ID, sequence, times, content hash, and previous hash.
2. Invalid input or insufficient authority is rejected without creating a record.
3. No event update or delete API exists.
4. An authorized reader can combine all supported filters and paginate deterministic results.
5. Verification reports an intact chain for untampered data.
6. After deliberate historical-row modification, verification identifies the first affected sequence and violation type.
7. Parallel writes produce one linear, verifiable chain.
8. Legitimate archiving does not produce a false chain-break result.
9. Redaction removes plaintext access, appends a receipt, and leaves verification successful.
10. An exported bundle passes an independent verifier; modification makes verification fail.
11. Account-data access attempts create compliance events without storing returned client data.
12. OpenAPI describes schemas, errors, filters, pagination, verification, redaction, export, and JWT security.
13. The application runs with H2 locally and PostgreSQL through configuration only.
14. Tests cover canonical hashing, verification, tamper detection, authorization, filters, concurrency, retention, redaction, and export.

Current validation note: H2 concurrency and export verification are automated. PostgreSQL migration and concurrency tests are implemented with Testcontainers and require Docker to execute.

## 7. Questions requiring product/security/compliance confirmation

1. Is the chain global, per tenant, or per account domain?
2. What volume, concurrency, latency, availability, and retention targets apply?
3. Which timestamp is legally authoritative, and what occurrence-time clock skew is allowed?
4. Which paths are sensitive, and who requests and approves redaction?
5. Is redaction irreversible erasure, reversible masking, or dependent on legal hold?
6. Where must archives and keys reside, and how are restoration and legal hold handled?
7. Must exports prove completeness as well as integrity, and which signer is trusted?
8. Which identity provider, issuer, audience, key rotation, and roles are authoritative?
9. Does access include APIs, UI views, batch jobs, direct database queries, and third parties?
10. Which jurisdictions, regulator formats, report periods, and retention rules apply?

## 8. Deferred production concerns

- Multi-region ordering and replication.
- Tenant-partitioned chains and tenant-specific keys.
- Managed KMS or hardware-security-module integration.
- External WORM archive storage.
- Enterprise identity-provider deployment.
- Regulatory certification of crypto-shredding.
- Mathematical completeness proofs for arbitrary filtered exports without a trusted signer or authenticated data structure.
