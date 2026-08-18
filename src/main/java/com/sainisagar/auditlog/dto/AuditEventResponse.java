package com.sainisagar.auditlog.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        Long sequenceNumber,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        JsonNode payload,
        Instant occurredAt,
        Instant recordedAt,
        String previousHash,
        String contentHash,
        Integer hashVersion,
        boolean archived,
        Instant archivedAt
) {
}
