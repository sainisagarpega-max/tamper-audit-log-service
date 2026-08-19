package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.ChainVerificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres-test")
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
class PostgreSqlAuditChainIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AuditEventService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetChain() {
        jdbcTemplate.update("delete from redaction_receipts");
        jdbcTemplate.update("delete from audit_events");
        jdbcTemplate.update("update chain_state set last_sequence = 0, last_hash = ? where chain_name = 'GLOBAL'",
                AuditHashService.GENESIS_HASH);
    }

    @Test
    void flywayCreatesExpectedPostgreSqlSchema() {
        Integer eventTable = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'audit_events'
                """, Integer.class);
        Integer chainStateTable = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'chain_state'
                """, Integer.class);

        assertThat(eventTable).isEqualTo(1);
        assertThat(chainStateTable).isEqualTo(1);
        assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(0));
    }

    @Test
    void concurrentPostgreSqlWritesRemainUniqueLinearAndVerifiable() {
        int writeCount = 20;
        var executor = Executors.newFixedThreadPool(10);
        try {
            List<CompletableFuture<AuditEventResponse>> futures = new ArrayList<>();
            for (int index = 0; index < writeCount; index++) {
                int eventNumber = index;
                futures.add(CompletableFuture.supplyAsync(() -> service.create(request(eventNumber)), executor));
            }
            List<AuditEventResponse> events = futures.stream().map(CompletableFuture::join)
                    .sorted(Comparator.comparing(AuditEventResponse::sequenceNumber)).toList();

            assertThat(events).extracting(AuditEventResponse::sequenceNumber)
                    .containsExactlyElementsOf(LongStream.rangeClosed(1, writeCount).boxed().toList())
                    .doesNotHaveDuplicates();
            assertThat(events.getFirst().previousHash()).isEqualTo(AuditHashService.GENESIS_HASH);
            for (int index = 1; index < events.size(); index++) {
                assertThat(events.get(index).previousHash()).isEqualTo(events.get(index - 1).contentHash());
            }
            assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(writeCount));
        } finally {
            executor.shutdownNow();
        }
    }

    private AuditEventRequest request(int eventNumber) {
        return new AuditEventRequest("READ", "postgres-actor-" + eventNumber, "ACCOUNT",
                "postgres-account-" + eventNumber, objectMapper.readTree("{\"result\":\"OK\"}"),
                Instant.parse("2026-08-19T05:30:00Z"));
    }
}
