package com.sainisagar.auditlog.controller;

import com.sainisagar.auditlog.service.AuditHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused",
        "app.retention.days=0"
})
class AuditEventApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbcTemplate.update("delete from redaction_receipts");
        jdbcTemplate.update("delete from audit_events");
        jdbcTemplate.update("update chain_state set last_sequence = 0, last_hash = ? where chain_name = 'GLOBAL'",
                AuditHashService.GENESIS_HASH);
    }

    @Test
    void writerCanAppendAndReaderCanQueryAndVerify() throws Exception {
        mockMvc.perform(post("/api/v1/audit-events")
                        .with(jwtWithRole("AUDIT_WRITER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventType": "READ",
                                  "actorId": "actor-1",
                                  "resourceType": "ACCOUNT",
                                  "resourceId": "account-1",
                                  "payload": {"result": "OK"},
                                  "occurredAt": "2026-08-18T09:59:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.previousHash").value(AuditHashService.GENESIS_HASH))
                .andExpect(jsonPath("$.contentHash").isString());

        mockMvc.perform(get("/api/v1/audit-events")
                        .with(jwtWithRole("AUDIT_READER"))
                        .param("actorId", "actor-1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].resourceId").value("account-1"));

        mockMvc.perform(get("/api/v1/audit-events/verify").with(jwtWithRole("AUDIT_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.recordsChecked").value(1));
    }

    @Test
    void APIRejectsUnauthenticatedInvalidAndUnauthorizedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/audit-events")
                        .with(jwtWithRole("AUDIT_READER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/audit-events")
                        .with(jwtWithRole("AUDIT_WRITER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.eventType").exists())
                .andExpect(jsonPath("$.errors.payload").exists());
    }

    @Test
    void verifyApiReportsDatabaseTampering() throws Exception {
        mockMvc.perform(post("/api/v1/audit-events")
                        .with(jwtWithRole("AUDIT_WRITER"))
                        .contentType("application/json")
                        .content("""
                                {"eventType":"READ","actorId":"actor-1","resourceType":"ACCOUNT",
                                 "resourceId":"account-1","payload":{"result":"OK"}}
                                """))
                .andExpect(status().isCreated());
        jdbcTemplate.update("update audit_events set actor_id = 'attacker' where sequence_number = 1");

        mockMvc.perform(get("/api/v1/audit-events/verify").with(jwtWithRole("AUDIT_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.firstBrokenSequence").value(1))
                .andExpect(jsonPath("$.violationType").value("CONTENT_HASH_MISMATCH"));
    }

    @Test
    void swaggerIsPublicAndDocumentsJwtBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Tamper-Evident Audit Log API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithRole(String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject("test-user").claim("roles", java.util.List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
