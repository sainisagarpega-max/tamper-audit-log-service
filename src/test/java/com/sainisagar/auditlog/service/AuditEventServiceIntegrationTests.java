package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.ChainVerificationResponse;
import com.sainisagar.auditlog.dto.RedactionRequest;
import com.sainisagar.auditlog.dto.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused",
        "app.retention.days=0"
})
class AuditEventServiceIntegrationTests {

    @Autowired
    private AuditEventService service;

    @Autowired
    private AuditHashService hashService;

    @Autowired
    private RedactionService redactionService;

    @Autowired
    private ExportService exportService;

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetChain() {
        jdbcTemplate.update("delete from redaction_receipts");
        jdbcTemplate.update("delete from audit_events");
        jdbcTemplate.update("update chain_state set last_sequence = 0, last_hash = ? where chain_name = 'GLOBAL'",
                AuditHashService.GENESIS_HASH);
    }

    @Test
    void appendsEventsIntoOneLinkedChain() {
        AuditEventResponse first = service.create(request("LOGIN", "actor-1", "account-1", "{\"b\":2,\"a\":1}"));
        AuditEventResponse second = service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));

        assertThat(first.sequenceNumber()).isEqualTo(1);
        assertThat(first.previousHash()).isEqualTo(AuditHashService.GENESIS_HASH);
        assertThat(first.hashVersion()).isEqualTo(AuditHashService.CURRENT_VERSION);
        assertThat(first.payload().toString()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(second.sequenceNumber()).isEqualTo(2);
        assertThat(second.previousHash()).isEqualTo(first.contentHash());
        assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(2));
    }

    @Test
    void queriesUsingCombinedFiltersAndPagination() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        service.create(request("UPDATE", "actor-2", "account-2", "{\"result\":\"OK\"}"));
        service.create(request("READ", "actor-1", "account-3", "{\"result\":\"OK\"}"));

        var result = service.query("actor-1", "ACCOUNT", null, "READ", null, null, 0, 1);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).extracting(AuditEventResponse::sequenceNumber).containsExactly(1L);
    }

    @Test
    void detectsDirectDatabaseTampering() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        service.create(request("READ", "actor-2", "account-2", "{\"result\":\"OK\"}"));
        assertThat(service.verify().intact()).isTrue();

        jdbcTemplate.update("update audit_events set event_type = 'TAMPERED' where sequence_number = 1");

        ChainVerificationResponse result = service.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenSequence()).isEqualTo(1);
        assertThat(result.violationType()).isEqualTo(ViolationType.CONTENT_HASH_MISMATCH);
    }

    @Test
    void canonicalPayloadAndHashAreIndependentOfObjectKeyOrder() throws Exception {
        var firstPayload = objectMapper.readTree("{\"outer\":{\"b\":2,\"a\":1},\"z\":true}");
        var secondPayload = objectMapper.readTree("{\"z\":true,\"outer\":{\"a\":1,\"b\":2}}");
        String firstCanonical = hashService.canonicalizePayload(firstPayload);
        String secondCanonical = hashService.canonicalizePayload(secondPayload);
        Instant recordedAt = Instant.parse("2026-08-18T10:00:00Z");

        String firstHash = hashService.calculate(1, "READ", "actor", "ACCOUNT", "account-1",
                firstCanonical, null, recordedAt, AuditHashService.GENESIS_HASH, 1);
        String secondHash = hashService.calculate(1, "READ", "actor", "ACCOUNT", "account-1",
                secondCanonical, null, recordedAt, AuditHashService.GENESIS_HASH, 1);

        assertThat(firstCanonical).isEqualTo(secondCanonical);
        assertThat(firstHash).isEqualTo(secondHash).hasSize(64);
    }

    @Test
    void redactsSensitiveFieldWithoutBreakingChain() {
        AuditEventResponse event = service.create(request("READ", "actor-1", "account-1",
                "{\"customer\":{\"accountNumber\":\"123456789\",\"name\":\"Sam\"}}"));

        var receipt = redactionService.redact(event.id(),
                new RedactionRequest("/customer/accountNumber", "privacy-admin", "privacy request"));

        var redacted = service.query(null, null, "account-1", null, null, null, 0, 20, true)
                .getContent().getFirst();
        assertThat(redacted.payload().toString()).doesNotContain("123456789");
        assertThat(redacted.payload().at("/customer/accountNumber/_redacted").asBoolean()).isTrue();
        assertThat(receipt.saltedValueHash()).hasSize(64);
        assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(2));
    }

    @Test
    void archivesExpiredEventsWithoutChangingChainIntegrity() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        var result = retentionService.archiveExpired();

        assertThat(result.recordsArchived()).isEqualTo(1);
        assertThat(service.query(null, null, null, null, null, null, 0, 20).getTotalElements()).isZero();
        assertThat(service.query(null, null, null, null, null, null, 0, 20, true).getTotalElements()).isEqualTo(1);
        assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(1));
    }

    @Test
    void exportsFilteredRecordsWithFullChainMetadata() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        AuditEventResponse second = service.create(request("UPDATE", "actor-2", "account-2",
                "{\"result\":\"OK\"}"));

        var bundle = exportService.export("actor-1", null);

        assertThat(bundle.records()).hasSize(1);
        assertThat(bundle.records().getFirst().actorId()).isEqualTo("actor-1");
        assertThat(bundle.chainMetadata()).hasSize(2);
        assertThat(bundle.chainHeadHash()).isEqualTo(second.contentHash());
        assertThat(bundle.bundleHash()).hasSize(64);
    }

    private AuditEventRequest request(String eventType, String actorId, String resourceId, String payload) {
        return new AuditEventRequest(eventType, actorId, "ACCOUNT", resourceId,
                objectMapper.readTree(payload), Instant.parse("2026-08-18T09:59:00Z"));
    }
}
