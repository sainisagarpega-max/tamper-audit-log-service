package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.RedactionRequest;
import com.sainisagar.auditlog.dto.RedactionResponse;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.entity.RedactionReceipt;
import com.sainisagar.auditlog.repository.AuditEventRepository;
import com.sainisagar.auditlog.repository.RedactionReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class RedactionService {

    private final AuditEventRepository eventRepository;
    private final RedactionReceiptRepository receiptRepository;
    private final AuditEventService eventService;
    private final AuditHashService hashService;
    private final RedactionProofService proofService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public RedactionService(AuditEventRepository eventRepository, RedactionReceiptRepository receiptRepository,
                            AuditEventService eventService, AuditHashService hashService,
                            RedactionProofService proofService, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.receiptRepository = receiptRepository;
        this.eventService = eventService;
        this.hashService = hashService;
        this.proofService = proofService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RedactionResponse redact(long eventId, RedactionRequest request) {
        AuditEvent target = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Audit event not found: " + eventId));
        ObjectNode payload = requireObject(objectMapper.readTree(target.getPayload()));
        PointerTarget pointer = locate(payload, request.jsonPointer());
        JsonNode originalValue = pointer.parent().get(pointer.field());
        if (originalValue == null || originalValue.isMissingNode()) {
            throw new IllegalArgumentException("JSON pointer does not identify an existing field");
        }
        if (originalValue.isObject() && originalValue.path("_redacted").asBoolean(false)) {
            throw new IllegalArgumentException("The selected field is already redacted");
        }

        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
        String originalCanonical = objectMapper.writeValueAsString(originalValue);
        String saltedValueHash = hashService.digestUtf8(salt + "|" + originalCanonical);

        ObjectNode placeholder = objectMapper.createObjectNode();
        placeholder.put("_redacted", true);
        placeholder.put("algorithm", "SHA-256");
        placeholder.put("salt", salt);
        placeholder.put("saltedHash", saltedValueHash);
        pointer.parent().set(pointer.field(), placeholder);

        String redactedPayload = hashService.canonicalizePayload(payload);
        String redactedPayloadHash = hashService.digestUtf8(redactedPayload);
        Instant redactedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String receiptHash = proofService.receiptHash(target.getSequenceNumber(), target.getContentHash(),
                request.jsonPointer(), saltedValueHash, redactedPayloadHash, request.requestedBy(), request.reason(),
                redactedAt.toString());

        target.redactPayload(redactedPayload);
        RedactionReceipt receipt = receiptRepository.save(new RedactionReceipt(target.getSequenceNumber(),
                target.getContentHash(), request.jsonPointer(), saltedValueHash, redactedPayloadHash,
                request.requestedBy(), request.reason(), redactedAt, receiptHash));
        receiptRepository.flush();

        ObjectNode receiptPayload = objectMapper.createObjectNode();
        receiptPayload.put("receiptId", receipt.getId());
        receiptPayload.put("targetSequence", target.getSequenceNumber());
        receiptPayload.put("jsonPointer", request.jsonPointer());
        receiptPayload.put("originalContentHash", target.getContentHash());
        receiptPayload.put("redactedPayloadHash", redactedPayloadHash);
        receiptPayload.put("saltedValueHash", saltedValueHash);
        receiptPayload.put("receiptHash", receiptHash);
        receiptPayload.put("reason", request.reason());
        eventService.create(new AuditEventRequest("PAYLOAD_REDACTED", request.requestedBy(),
                "AUDIT_EVENT", String.valueOf(target.getId()), receiptPayload, redactedAt));

        return new RedactionResponse(receipt.getId(), target.getSequenceNumber(), request.jsonPointer(),
                saltedValueHash, receiptHash, redactedAt);
    }

    private ObjectNode requireObject(JsonNode node) {
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("Stored payload is not a JSON object");
        }
        return objectNode;
    }

    private PointerTarget locate(ObjectNode root, String pointer) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() < 2) {
            throw new IllegalArgumentException("jsonPointer must start with '/' and identify a field");
        }
        String[] segments = pointer.substring(1).split("/", -1);
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = decode(segments[index]);
            JsonNode child = current.get(segment);
            if (!(child instanceof ObjectNode childObject)) {
                throw new IllegalArgumentException("jsonPointer traverses a missing or non-object field");
            }
            current = childObject;
        }
        return new PointerTarget(current, decode(segments[segments.length - 1]));
    }

    private String decode(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }

    private record PointerTarget(ObjectNode parent, String field) {
    }
}
