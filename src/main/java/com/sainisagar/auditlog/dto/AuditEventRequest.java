package com.sainisagar.auditlog.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AuditEventRequest(
        @NotBlank @Size(max = 100) String eventType,
        @NotBlank @Size(max = 100) String actorId,
        @NotBlank @Size(max = 100) String resourceType,
        @NotBlank @Size(max = 150) String resourceId,
        @NotNull JsonNode payload,
        @PastOrPresent Instant occurredAt
) {
}
