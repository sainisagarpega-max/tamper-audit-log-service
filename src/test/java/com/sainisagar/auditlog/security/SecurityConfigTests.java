package com.sainisagar.auditlog.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void convertsRolesClaimToSpringRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("saini-sagar")
                .claim("roles", List.of("AUDIT_WRITER", "AUDIT_ADMIN"))
                .issuedAt(Instant.parse("2026-08-18T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-18T11:00:00Z"))
                .build();

        var authentication = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("saini-sagar");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .filteredOn(authority -> ((String) authority).startsWith("ROLE_"))
                .containsExactlyInAnyOrder("ROLE_AUDIT_WRITER", "ROLE_AUDIT_ADMIN");
    }

    @Test
    void tokenWithoutRolesReceivesNoApplicationAuthority() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("saini-sagar")
                .issuedAt(Instant.parse("2026-08-18T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-18T11:00:00Z"))
                .build();

        var authentication = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .noneMatch(authority -> ((String) authority).startsWith("ROLE_"));
    }

    @Test
    void audienceValidatorAcceptsRequiredAudience() {
        Jwt jwt = jwtWithAudience(List.of("another-api", "audit-log-api"));

        assertThat(new JwtAudienceValidator("audit-log-api").validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void audienceValidatorRejectsMissingRequiredAudience() {
        Jwt jwt = jwtWithAudience(List.of("another-api"));

        assertThat(new JwtAudienceValidator("audit-log-api").validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("saini-sagar")
                .audience(audience)
                .issuedAt(Instant.parse("2026-08-18T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-18T11:00:00Z"))
                .build();
    }
}
