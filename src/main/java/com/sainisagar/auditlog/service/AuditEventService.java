package com.sainisagar.auditlog.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuditEventService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AuditEventService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    AuditEventService(AuditEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public synchronized AuditEventResponse create(AuditEventRequest request) {
        AuditEvent previous = repository.findTopByOrderBySequenceNumberDesc().orElse(null);
        long sequence = previous == null ? 1L : previous.getSequenceNumber() + 1L;
        String previousHash = previous == null ? GENESIS_HASH : previous.getContentHash();
        Instant recordedAt = clock.instant();
        String payload = canonicalPayload(request.payload());
        String contentHash = calculateHash(sequence, request, payload, recordedAt, previousHash);

        AuditEvent saved = repository.save(new AuditEvent(
                sequence, request.eventType(), request.actorId(), request.resourceType(), request.resourceId(),
                payload, request.occurredAt(), recordedAt, previousHash, contentHash));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    private String canonicalPayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payload cannot be serialized", exception);
        }
    }

    private String calculateHash(long sequence, AuditEventRequest request, String payload,
                                 Instant recordedAt, String previousHash) {
        String canonical = String.join("|",
                "v1", Long.toString(sequence), request.eventType(), request.actorId(), request.resourceType(),
                request.resourceId(), payload, String.valueOf(request.occurredAt()), recordedAt.toString(), previousHash);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        try {
            return new AuditEventResponse(event.getId(), event.getSequenceNumber(), event.getEventType(),
                    event.getActorId(), event.getResourceType(), event.getResourceId(),
                    objectMapper.readTree(event.getPayload()), event.getOccurredAt(), event.getRecordedAt(),
                    event.getPreviousHash(), event.getContentHash());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored payload is invalid", exception);
        }
    }
}
