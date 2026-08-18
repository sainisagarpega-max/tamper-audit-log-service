package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.LocalTokenRequest;
import com.sainisagar.auditlog.dto.LocalTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
@Profile("local")
public class LocalTokenService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "AUDIT_WRITER", "AUDIT_READER", "AUDIT_ADMIN", "AUDIT_COMPLIANCE");

    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlMinutes;
    private final Clock clock = Clock.systemUTC();

    public LocalTokenService(JwtEncoder encoder,
                             @Value("${app.jwt.local.issuer}") String issuer,
                             @Value("${app.jwt.local.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
    }

    public LocalTokenResponse issue(LocalTokenRequest request) {
        if (!ALLOWED_ROLES.containsAll(request.roles())) {
            throw new IllegalArgumentException("Allowed roles are: " + String.join(", ", ALLOWED_ROLES));
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(request.subject())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", request.roles())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LocalTokenResponse(token, "Bearer", ttlMinutes * 60, expiresAt);
    }
}
