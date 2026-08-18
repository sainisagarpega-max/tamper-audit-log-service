package com.sainisagar.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "audit_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_audit_event_sequence", columnNames = "sequence_number")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Long sequenceNumber;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 100)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 150)
    private String resourceId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "hash_version", nullable = false, updatable = false)
    private Integer hashVersion;

    protected AuditEvent() {
    }

    public AuditEvent(Long sequenceNumber, String eventType, String actorId, String resourceType,
                      String resourceId, String payload, Instant occurredAt, Instant recordedAt,
                      String previousHash, String contentHash, Integer hashVersion) {
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.previousHash = previousHash;
        this.contentHash = contentHash;
        this.hashVersion = hashVersion;
    }

    public Long getId() { return id; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getPreviousHash() { return previousHash; }
    public String getContentHash() { return contentHash; }
    public Integer getHashVersion() { return hashVersion; }
}
