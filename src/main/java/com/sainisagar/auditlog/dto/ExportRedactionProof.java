package com.sainisagar.auditlog.dto;

import java.time.Instant;

public record ExportRedactionProof(
        Long targetSequence,
        String originalContentHash,
        String jsonPointer,
        String saltedValueHash,
        String redactedPayloadHash,
        String requestedBy,
        String reason,
        Instant redactedAt,
        String receiptHash,
        AuditEventResponse anchorEvent
) {
}
