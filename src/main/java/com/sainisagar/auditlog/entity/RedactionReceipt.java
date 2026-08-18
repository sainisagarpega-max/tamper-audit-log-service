package com.sainisagar.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "redaction_receipts")
public class RedactionReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_sequence", nullable = false)
    private Long targetSequence;

    @Column(name = "original_content_hash", nullable = false, length = 64)
    private String originalContentHash;

    @Column(name = "json_pointer", nullable = false, length = 500)
    private String jsonPointer;

    @Column(name = "salted_value_hash", nullable = false, length = 64)
    private String saltedValueHash;

    @Column(name = "redacted_payload_hash", nullable = false, length = 64)
    private String redactedPayloadHash;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "redacted_at", nullable = false)
    private Instant redactedAt;

    @Column(name = "receipt_hash", nullable = false, unique = true, length = 64)
    private String receiptHash;

    protected RedactionReceipt() {
    }

    public RedactionReceipt(Long targetSequence, String originalContentHash, String jsonPointer,
                            String saltedValueHash, String redactedPayloadHash, String requestedBy,
                            String reason, Instant redactedAt, String receiptHash) {
        this.targetSequence = targetSequence;
        this.originalContentHash = originalContentHash;
        this.jsonPointer = jsonPointer;
        this.saltedValueHash = saltedValueHash;
        this.redactedPayloadHash = redactedPayloadHash;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.redactedAt = redactedAt;
        this.receiptHash = receiptHash;
    }

    public Long getId() { return id; }
    public Long getTargetSequence() { return targetSequence; }
    public String getOriginalContentHash() { return originalContentHash; }
    public String getJsonPointer() { return jsonPointer; }
    public String getSaltedValueHash() { return saltedValueHash; }
    public String getRedactedPayloadHash() { return redactedPayloadHash; }
    public String getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public Instant getRedactedAt() { return redactedAt; }
    public String getReceiptHash() { return receiptHash; }
}
