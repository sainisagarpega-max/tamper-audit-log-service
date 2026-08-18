package com.sainisagar.auditlog.dto;

import java.time.Instant;

public record RetentionResponse(
        long recordsArchived,
        Instant cutoff,
        Instant completedAt
) {
}
