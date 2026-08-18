package com.sainisagar.auditlog.dto;

import java.time.Instant;

public record LocalTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt
) {
}
