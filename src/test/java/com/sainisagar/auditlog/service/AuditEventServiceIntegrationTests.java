package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.AccessChannel;
import com.sainisagar.auditlog.dto.AccessOutcome;
import com.sainisagar.auditlog.dto.AccountAccessAction;
import com.sainisagar.auditlog.dto.ClientAccountAccessRequest;
import com.sainisagar.auditlog.dto.ChainVerificationResponse;
import com.sainisagar.auditlog.dto.RedactionRequest;
import com.sainisagar.auditlog.dto.ViolationType;
import com.sainisagar.auditlog.dto.AuditExportBundle;
import com.sainisagar.auditlog.dto.ExportRedactionProof;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private ExportVerificationService exportVerificationService;

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private ComplianceAccessService complianceAccessService;

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
    void detectsDeletedRecordAsSequenceGap() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        service.create(request("UPDATE", "actor-2", "account-2", "{\"result\":\"OK\"}"));
        service.create(request("DELETE", "actor-3", "account-3", "{\"result\":\"OK\"}"));

        jdbcTemplate.update("delete from audit_events where sequence_number = 2");

        ChainVerificationResponse result = service.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.recordsChecked()).isEqualTo(1);
        assertThat(result.firstBrokenSequence()).isEqualTo(3);
        assertThat(result.violationType()).isEqualTo(ViolationType.SEQUENCE_GAP);
    }

    @Test
    void detectsChangedChainLink() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        service.create(request("UPDATE", "actor-2", "account-2", "{\"result\":\"OK\"}"));

        jdbcTemplate.update("update audit_events set previous_hash = ? where sequence_number = 2",
                "f".repeat(64));

        ChainVerificationResponse result = service.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenSequence()).isEqualTo(2);
        assertThat(result.violationType()).isEqualTo(ViolationType.PREVIOUS_HASH_MISMATCH);
    }

    @Test
    void detectsDeletionOfLastEventUsingPersistedChainHead() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        service.create(request("UPDATE", "actor-2", "account-2", "{\"result\":\"OK\"}"));

        jdbcTemplate.update("delete from audit_events where sequence_number = 2");

        ChainVerificationResponse result = service.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.recordsChecked()).isEqualTo(1);
        assertThat(result.firstBrokenSequence()).isEqualTo(2);
        assertThat(result.violationType()).isEqualTo(ViolationType.CHAIN_HEAD_MISMATCH);
    }

    @Test
    void detectsDeletionOfCompleteChainUsingPersistedChainHead() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));

        jdbcTemplate.update("delete from audit_events");

        ChainVerificationResponse result = service.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.recordsChecked()).isZero();
        assertThat(result.firstBrokenSequence()).isEqualTo(1);
        assertThat(result.violationType()).isEqualTo(ViolationType.CHAIN_HEAD_MISMATCH);
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
        AuditExportBundle bundle = exportService.export("actor-1", null);
        assertThat(bundle.bundleVersion()).isEqualTo("2");
        assertThat(bundle.redactionProofs()).hasSize(1);
        assertThat(bundle.redactionProofs().getFirst().anchorEvent().eventType()).isEqualTo("PAYLOAD_REDACTED");
        assertThat(exportVerificationService.verify(bundle).valid()).isTrue();

        ExportRedactionProof proof = bundle.redactionProofs().getFirst();
        ExportRedactionProof alteredProof = new ExportRedactionProof(proof.targetSequence(),
                proof.originalContentHash(), proof.jsonPointer(), proof.saltedValueHash(),
                proof.redactedPayloadHash(), proof.requestedBy(), "altered reason", proof.redactedAt(),
                proof.receiptHash(), proof.anchorEvent());
        AuditExportBundle alteredWithoutHash = new AuditExportBundle(bundle.bundleVersion(), bundle.exportedAt(),
                bundle.actorId(), bundle.resourceId(), bundle.hashAlgorithm(), bundle.genesisHash(), bundle.records(),
                List.of(alteredProof), bundle.chainMetadata(), bundle.chainHeadHash(), null);
        AuditExportBundle altered = new AuditExportBundle(bundle.bundleVersion(), bundle.exportedAt(), bundle.actorId(),
                bundle.resourceId(), bundle.hashAlgorithm(), bundle.genesisHash(), bundle.records(),
                alteredWithoutHash.redactionProofs(), bundle.chainMetadata(), bundle.chainHeadHash(),
                exportService.calculateBundleHash(alteredWithoutHash));
        assertThat(exportVerificationService.verify(altered).valid()).isFalse();
    }

    @Test
    void rejectsMissingAndRepeatedRedactions() {
        AuditEventResponse event = service.create(request("READ", "actor-1", "account-1",
                "{\"customer\":{\"accountNumber\":\"123456789\"}}"));
        RedactionRequest valid = new RedactionRequest("/customer/accountNumber", "privacy-admin",
                "privacy request");

        assertThatThrownBy(() -> redactionService.redact(event.id(),
                new RedactionRequest("/customer/missing", "privacy-admin", "privacy request")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing field");

        redactionService.redact(event.id(), valid);
        assertThatThrownBy(() -> redactionService.redact(event.id(), valid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already redacted");
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
    void retentionIsIdempotentForAlreadyArchivedEvents() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));

        assertThat(retentionService.archiveExpired().recordsArchived()).isEqualTo(1);
        assertThat(retentionService.archiveExpired().recordsArchived()).isZero();
        assertThat(service.query(null, null, null, null, null, null, 0, 20, true).getTotalElements())
                .isEqualTo(1);
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
        assertThat(exportVerificationService.verify(bundle).valid()).isTrue();
    }

    @Test
    void exportVerifierRejectsModifiedRecordEvenWhenBundleHashIsRecomputed() {
        service.create(request("READ", "actor-1", "account-1", "{\"result\":\"OK\"}"));
        AuditExportBundle original = exportService.export("actor-1", null);
        AuditEventResponse record = original.records().getFirst();
        AuditEventResponse modifiedRecord = new AuditEventResponse(record.id(), record.sequenceNumber(),
                record.eventType(), "attacker", record.resourceType(), record.resourceId(), record.payload(),
                record.occurredAt(), record.recordedAt(), record.previousHash(), record.contentHash(),
                record.hashVersion(), record.archived(), record.archivedAt());
        AuditExportBundle modifiedWithoutHash = new AuditExportBundle(original.bundleVersion(), original.exportedAt(),
                original.actorId(), original.resourceId(), original.hashAlgorithm(), original.genesisHash(),
                List.of(modifiedRecord), original.redactionProofs(), original.chainMetadata(),
                original.chainHeadHash(), null);
        AuditExportBundle modified = new AuditExportBundle(original.bundleVersion(), original.exportedAt(),
                original.actorId(), original.resourceId(), original.hashAlgorithm(), original.genesisHash(),
                modifiedWithoutHash.records(), original.redactionProofs(), original.chainMetadata(),
                original.chainHeadHash(), exportService.calculateBundleHash(modifiedWithoutHash));

        var result = exportVerificationService.verify(modified);

        assertThat(result.valid()).isFalse();
        assertThat(result.violation()).isEqualTo("RECORD_CONTENT_HASH_MISMATCH");
        assertThat(result.sequenceNumber()).isEqualTo(1);
    }

    @Test
    void concurrentWritesProduceOneLinearChain() {
        int writeCount = 12;
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<CompletableFuture<AuditEventResponse>> futures = new ArrayList<>();
            for (int index = 0; index < writeCount; index++) {
                int eventNumber = index;
                futures.add(CompletableFuture.supplyAsync(() -> service.create(request("READ",
                        "actor-" + eventNumber, "account-" + eventNumber, "{\"result\":\"OK\"}")), executor));
            }
            List<AuditEventResponse> events = futures.stream().map(CompletableFuture::join)
                    .sorted(Comparator.comparing(AuditEventResponse::sequenceNumber)).toList();

            assertThat(events).extracting(AuditEventResponse::sequenceNumber)
                    .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, writeCount).boxed().toList());
            assertThat(events).extracting(AuditEventResponse::sequenceNumber).doesNotHaveDuplicates();
            assertThat(events.getFirst().previousHash()).isEqualTo(AuditHashService.GENESIS_HASH);
            for (int index = 1; index < events.size(); index++) {
                assertThat(events.get(index).previousHash()).isEqualTo(events.get(index - 1).contentHash());
            }
            assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(writeCount));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recordsAndReportsClientAccountAccessWithoutClientData() {
        complianceAccessService.record(new ClientAccountAccessRequest("account-1", "advisor-1",
                AccountAccessAction.VIEW, "Customer support", AccessChannel.WEB, AccessOutcome.ALLOWED,
                "correlation-1", Instant.parse("2026-08-18T09:59:00Z")));
        complianceAccessService.record(new ClientAccountAccessRequest("account-2", "advisor-2",
                AccountAccessAction.SEARCH, "Fraud review", AccessChannel.API, AccessOutcome.DENIED,
                "correlation-2", Instant.parse("2026-08-18T09:59:00Z")));

        var report = complianceAccessService.report("account-1", null, null, null, 0, 20, "compliance-user");

        assertThat(report.getTotalElements()).isEqualTo(1);
        assertThat(report.getContent().getFirst().actorId()).isEqualTo("advisor-1");
        assertThat(report.getContent().getFirst().purpose()).isEqualTo("Customer support");
        var rawEvent = service.query("advisor-1", "CLIENT_ACCOUNT", "account-1", "CLIENT_ACCOUNT_ACCESS",
                null, null, 0, 20, true).getContent().getFirst();
        assertThat(rawEvent.payload().toString()).doesNotContain("clientData", "accountBalance");
        assertThat(service.query("compliance-user", "COMPLIANCE_REPORT", null,
                "COMPLIANCE_REPORT_ACCESSED", null, null, 0, 20, true).getTotalElements()).isEqualTo(1);
        assertThat(service.verify()).isEqualTo(ChainVerificationResponse.intact(3));
    }

    private AuditEventRequest request(String eventType, String actorId, String resourceId, String payload) {
        return new AuditEventRequest(eventType, actorId, "ACCOUNT", resourceId,
                objectMapper.readTree(payload), Instant.parse("2026-08-18T09:59:00Z"));
    }
}
