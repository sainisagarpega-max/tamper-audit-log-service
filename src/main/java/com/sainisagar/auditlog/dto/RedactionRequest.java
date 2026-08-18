package com.sainisagar.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedactionRequest(
        @NotBlank @Size(max = 500) String jsonPointer,
        @NotBlank @Size(max = 100) String requestedBy,
        @NotBlank @Size(max = 500) String reason
) {
}
