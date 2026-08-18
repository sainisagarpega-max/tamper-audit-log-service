package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.entity.AuditEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

@Component
public class AuditHashService {

    public static final int CURRENT_VERSION = 1;
    public static final String GENESIS_HASH = "0".repeat(64);

    private final ObjectMapper objectMapper;

    public AuditHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalizePayload(JsonNode payload) {
        return objectMapper.writeValueAsString(sortNode(payload));
    }

    public String calculate(long sequence, String eventType, String actorId, String resourceType,
                            String resourceId, String canonicalPayload, Instant occurredAt,
                            Instant recordedAt, String previousHash, int hashVersion) {
        if (hashVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported hash version: " + hashVersion);
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(hashVersion);
                output.writeLong(sequence);
                writeString(output, eventType);
                writeString(output, actorId);
                writeString(output, resourceType);
                writeString(output, resourceId);
                writeString(output, canonicalPayload);
                writeString(output, occurredAt == null ? null : occurredAt.toString());
                writeString(output, recordedAt.toString());
                writeString(output, previousHash);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to calculate audit hash", exception);
        }
    }

    public String recalculate(AuditEvent event) {
        return calculate(event.getSequenceNumber(), event.getEventType(), event.getActorId(),
                event.getResourceType(), event.getResourceId(), event.getPayload(), event.getOccurredAt(),
                event.getRecordedAt(), event.getPreviousHash(), event.getHashVersion());
    }

    private JsonNode sortNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            node.properties().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> sorted.set(entry.getKey(), sortNode(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sortNode(value)));
            return sorted;
        }
        return node;
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
