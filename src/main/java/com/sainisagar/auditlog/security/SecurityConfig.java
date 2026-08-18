package com.sainisagar.auditlog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("local"));
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
                            "/swagger-ui.html").permitAll();
                    if (localProfile) {
                        authorize.requestMatchers(PathRequest.toH2Console()).permitAll();
                    }
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/dev/token").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/audit-events").hasRole("AUDIT_WRITER")
                            .requestMatchers(HttpMethod.POST, "/api/v1/compliance/account-access")
                            .hasRole("AUDIT_WRITER")
                            .requestMatchers(HttpMethod.GET, "/api/v1/compliance/account-access")
                            .hasAnyRole("AUDIT_COMPLIANCE", "AUDIT_ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/v1/retention/**",
                                    "/api/v1/audit-events/*/redactions")
                            .hasRole("AUDIT_ADMIN")
                            .requestMatchers(HttpMethod.GET, "/api/v1/audit-events/**")
                            .hasAnyRole("AUDIT_READER", "AUDIT_ADMIN")
                            .anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        if (localProfile) {
            http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        }
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
