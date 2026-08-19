# Scenario B - Retention, Redaction, and Export

## 1. Retention policy

The prototype uses soft archival. Events remain in `audit_events` so the verifier can still walk the complete chain, but expired records receive `archived=true` and an `archivedAt` timestamp.

Configuration:

```text
AUDIT_RETENTION_DAYS=365
AUDIT_RETENTION_SCHEDULE=0 0 2 * * *
```

The scheduled job runs in UTC. An administrator can also trigger it through:

```text
POST /api/v1/retention/archive
```

Normal queries exclude archived records. `includeArchived=true` includes them. Archive metadata is intentionally outside hash version 1, so legitimate archival does not cause a false chain failure. Production database permissions and auditing must prevent unauthorized changes to archival metadata.

Trade-off: soft archival meets the prototype requirement but does not reduce primary database storage. A production archive should copy immutable rows to WORM/object storage, verify the copy, write a signed checkpoint, and only then remove active copies under a separate privileged workflow.

## 2. Structured redaction

Endpoint:

```text
POST /api/v1/audit-events/{eventId}/redactions
```

Example request:

```json
{
  "jsonPointer": "/customer/accountNumber",
  "requestedBy": "privacy-admin",
  "reason": "Approved privacy request"
}
```

The prototype supports RFC 6901-style object-field paths. Array positions are intentionally not supported in version 1.

### Redaction flow

1. Locate the selected payload field.
2. Generate a random 128-bit salt.
3. Calculate `SHA-256(base64url(salt) + "|" + canonicalOriginalValue)`.
4. Replace the value with a placeholder containing `_redacted`, algorithm, salt, and salted hash.
5. Keep the event's original `contentHash` and all existing chain links unchanged.
6. Store a redaction receipt containing the target sequence, original content hash, path, salted-value hash, redacted-payload hash, requester, reason, time, and receipt hash.
7. Append a new `PAYLOAD_REDACTED` event containing the receipt proof, anchoring the authorized change in the main chain.

Verification normally recomputes an event's original hash. When a redacted payload no longer matches, verification accepts it only if:

- The receipt references the event's original content hash.
- The current redacted payload matches the receipt's redacted-payload hash.
- The receipt hash recomputes correctly.
- A valid later `PAYLOAD_REDACTED` chain event contains that receipt hash.

Any missing or altered receipt, placeholder, or receipt event results in a content-hash failure.

### Redaction trade-offs

- The original plaintext is removed from the audit event and receipt.
- The salt prevents precomputed rainbow-table matching but does not protect low-entropy values from targeted guessing.
- The salted hash proves commitment to the removed value but cannot restore it.
- Database backups and replicas created before redaction may still contain plaintext and need an approved lifecycle.
- A production system should consider encryption with per-field keys and crypto-shredding when stronger erasure guarantees are required.
- Redaction is restricted to `AUDIT_ADMIN`.

## 3. Bulk export

Endpoint:

```text
GET /api/v1/audit-events/export?actorId={actorId}
GET /api/v1/audit-events/export?resourceId={resourceId}
```

Exactly one filter is required. Archived matching records are included.

Export format version 2 contains:

- Bundle format version and export timestamp.
- Actor or resource filter.
- SHA-256 algorithm and genesis definition.
- Full matching audit-event records.
- Redaction receipts and their complete `PAYLOAD_REDACTED` anchor events for matching redacted records.
- Sequence, previous hash, content hash, and hash version for every chain position.
- Chain-head hash at export time.
- SHA-256 hash of the canonical unsigned bundle.

The recipient can recompute each included event hash, confirm its hash occurs at the declared chain position, check chain-link continuity in the metadata, and recompute the bundle hash to detect modification after export.

Verification endpoint:

```text
POST /api/v1/audit-events/export/verify
```

The endpoint requires `AUDIT_READER` or `AUDIT_ADMIN`. It consumes an `AuditExportBundle` and returns `valid`, `violation`, and the affected `sequenceNumber`. Verification uses only bundle contents: it recomputes the outer bundle hash, validates metadata continuity from genesis through the declared head, and recomputes every included event hash. It does not modify or query stored audit events.

Limitations:

- The bundle hash provides integrity, not sender authenticity. Production exports should be digitally signed using a managed asymmetric key.
- Metadata-only entries prove chain linkage but cannot independently recompute non-exported event content.
- Proving that the exporter did not omit a matching event requires trusting the exporter or using a signed authenticated index/Merkle proof.
- The verifier establishes internal bundle consistency, not publisher identity. An attacker who can replace the entire unsigned bundle and its declared head can construct a different internally consistent bundle.
- A redacted record is accepted only when its current payload matches the receipt, the receipt hash recomputes, the anchor event contains that receipt hash, the anchor event hash recomputes, and the anchor is present at the declared chain position.
- Export authorization and delivery-channel encryption remain production responsibilities.

## 4. Scenario B acceptance evidence

Automated integration tests demonstrate:

- Expired events become archived and disappear from normal queries.
- Archived events remain available with `includeArchived=true`.
- Archival does not break chain verification.
- Redacted plaintext is no longer returned.
- A salted placeholder and immutable receipt are produced.
- The anchored redaction remains verifiable.
- Filtered export includes matching records, complete chain metadata, chain head, and a bundle hash.
- Untouched exports pass self-contained verification.
- Modifying an exported event fails verification even if the outer bundle hash is recomputed.
- Legitimately redacted exports pass verification; altering a receipt fails even if the outer bundle hash is recomputed.
