package com.sainisagar.auditlog.dto;

import java.time.Instant;

public record ClientAccountAccessResponse(
        Long auditEventId,
        Long sequenceNumber,
        String accountId,
        String actorId,
        AccountAccessAction action,
        String purpose,
        AccessChannel channel,
        AccessOutcome outcome,
        String correlationId,
        Instant occurredAt,
        Instant recordedAt
) {
}
