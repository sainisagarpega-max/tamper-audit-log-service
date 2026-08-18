package com.sainisagar.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ClientAccountAccessRequest(
        @NotBlank @Size(max = 150) String accountId,
        @NotBlank @Size(max = 100) String actorId,
        @NotNull AccountAccessAction action,
        @NotBlank @Size(max = 200) String purpose,
        @NotNull AccessChannel channel,
        @NotNull AccessOutcome outcome,
        @NotBlank @Size(max = 100) String correlationId,
        @PastOrPresent Instant occurredAt
) {
}
