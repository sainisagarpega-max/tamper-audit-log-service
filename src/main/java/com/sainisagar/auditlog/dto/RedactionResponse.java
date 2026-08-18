package com.sainisagar.auditlog.dto;

import java.time.Instant;

public record RedactionResponse(
        Long receiptId,
        Long targetSequence,
        String jsonPointer,
        String saltedValueHash,
        String receiptHash,
        Instant redactedAt
) {
}
