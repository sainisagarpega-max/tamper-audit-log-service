package com.sainisagar.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record LocalTokenRequest(
        @NotBlank @Size(max = 100) String subject,
        @NotEmpty @Size(max = 4) Set<@NotBlank String> roles
) {
}
