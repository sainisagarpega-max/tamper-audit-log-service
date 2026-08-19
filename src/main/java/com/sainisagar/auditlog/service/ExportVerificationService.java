package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.AuditExportBundle;
import com.sainisagar.auditlog.dto.ExportChainLink;
import com.sainisagar.auditlog.dto.ExportVerificationResponse;
import com.sainisagar.auditlog.dto.ExportRedactionProof;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportVerificationService {

    private final ExportService exportService;
    private final AuditHashService hashService;
    private final RedactionProofService redactionProofService;

    public ExportVerificationService(ExportService exportService, AuditHashService hashService,
                                     RedactionProofService redactionProofService) {
        this.exportService = exportService;
        this.hashService = hashService;
        this.redactionProofService = redactionProofService;
    }

    public ExportVerificationResponse verify(AuditExportBundle bundle) {
        if (bundle == null || bundle.bundleHash() == null || bundle.records() == null
                || bundle.redactionProofs() == null
                || bundle.chainMetadata() == null || bundle.chainHeadHash() == null) {
            return ExportVerificationResponse.invalid("MALFORMED_BUNDLE", null);
        }
        if (!"2".equals(bundle.bundleVersion()) || !"SHA-256".equals(bundle.hashAlgorithm())
                || !AuditHashService.GENESIS_HASH.equals(bundle.genesisHash())) {
            return ExportVerificationResponse.invalid("UNSUPPORTED_BUNDLE_FORMAT", null);
        }
        if (!constantTimeEquals(bundle.bundleHash(), exportService.calculateBundleHash(bundle))) {
            return ExportVerificationResponse.invalid("BUNDLE_HASH_MISMATCH", null);
        }

        List<ExportChainLink> links = bundle.chainMetadata();
        Map<Long, ExportChainLink> linksBySequence = new HashMap<>();
        String expectedPreviousHash = AuditHashService.GENESIS_HASH;
        long expectedSequence = 1;
        for (ExportChainLink link : links) {
            if (link == null || link.sequenceNumber() == null || link.previousHash() == null
                    || link.contentHash() == null || link.hashVersion() == null) {
                return ExportVerificationResponse.invalid("MALFORMED_CHAIN_METADATA", null);
            }
            if (link.sequenceNumber() != expectedSequence) {
                return ExportVerificationResponse.invalid("SEQUENCE_GAP", link.sequenceNumber());
            }
            if (link.hashVersion() != AuditHashService.CURRENT_VERSION) {
                return ExportVerificationResponse.invalid("UNSUPPORTED_HASH_VERSION", link.sequenceNumber());
            }
            if (!expectedPreviousHash.equals(link.previousHash())) {
                return ExportVerificationResponse.invalid("PREVIOUS_HASH_MISMATCH", link.sequenceNumber());
            }
            linksBySequence.put(link.sequenceNumber(), link);
            expectedPreviousHash = link.contentHash();
            expectedSequence++;
        }
        if (!expectedPreviousHash.equals(bundle.chainHeadHash())) {
            return ExportVerificationResponse.invalid("CHAIN_HEAD_MISMATCH", links.isEmpty() ? 0L : expectedSequence - 1);
        }

        for (AuditEventResponse record : bundle.records()) {
            if (record == null || record.sequenceNumber() == null || record.payload() == null
                    || record.recordedAt() == null || record.hashVersion() == null) {
                return ExportVerificationResponse.invalid("MALFORMED_RECORD", null);
            }
            ExportChainLink link = linksBySequence.get(record.sequenceNumber());
            if (link == null || !link.previousHash().equals(record.previousHash())
                    || !link.contentHash().equals(record.contentHash())
                    || !link.hashVersion().equals(record.hashVersion())) {
                return ExportVerificationResponse.invalid("RECORD_CHAIN_METADATA_MISMATCH", record.sequenceNumber());
            }
            String payload = hashService.canonicalizePayload(record.payload());
            String recalculated = hashService.calculate(record.sequenceNumber(), record.eventType(), record.actorId(),
                    record.resourceType(), record.resourceId(), payload, record.occurredAt(), record.recordedAt(),
                    record.previousHash(), record.hashVersion());
            if (!recalculated.equals(record.contentHash())
                    && !hasValidRedactionProof(record, bundle.redactionProofs(), linksBySequence)) {
                return ExportVerificationResponse.invalid("RECORD_CONTENT_HASH_MISMATCH", record.sequenceNumber());
            }
        }
        return ExportVerificationResponse.verified();
    }

    private boolean hasValidRedactionProof(AuditEventResponse record, List<ExportRedactionProof> proofs,
                                            Map<Long, ExportChainLink> linksBySequence) {
        return proofs.stream()
                .filter(proof -> record.sequenceNumber().equals(proof.targetSequence()))
                .sorted((first, second) -> second.redactedAt().compareTo(first.redactedAt()))
                .anyMatch(proof -> isValidRedactionProof(record, proof, linksBySequence));
    }

    private boolean isValidRedactionProof(AuditEventResponse record, ExportRedactionProof proof,
                                           Map<Long, ExportChainLink> linksBySequence) {
        if (proof.originalContentHash() == null || proof.redactedPayloadHash() == null
                || proof.receiptHash() == null || proof.redactedAt() == null || proof.anchorEvent() == null
                || !record.contentHash().equals(proof.originalContentHash())
                || !hashService.digestUtf8(hashService.canonicalizePayload(record.payload()))
                .equals(proof.redactedPayloadHash())) {
            return false;
        }
        String expectedReceiptHash = redactionProofService.receiptHash(proof.targetSequence(),
                proof.originalContentHash(), proof.jsonPointer(), proof.saltedValueHash(),
                proof.redactedPayloadHash(), proof.requestedBy(), proof.reason(), proof.redactedAt().toString());
        if (!expectedReceiptHash.equals(proof.receiptHash())) {
            return false;
        }
        AuditEventResponse anchor = proof.anchorEvent();
        ExportChainLink anchorLink = linksBySequence.get(anchor.sequenceNumber());
        if (anchorLink == null || !"PAYLOAD_REDACTED".equals(anchor.eventType())
                || !proof.receiptHash().equals(anchor.payload().path("receiptHash").asString(null))
                || !anchorLink.previousHash().equals(anchor.previousHash())
                || !anchorLink.contentHash().equals(anchor.contentHash())
                || !anchorLink.hashVersion().equals(anchor.hashVersion())) {
            return false;
        }
        String anchorPayload = hashService.canonicalizePayload(anchor.payload());
        String anchorHash = hashService.calculate(anchor.sequenceNumber(), anchor.eventType(), anchor.actorId(),
                anchor.resourceType(), anchor.resourceId(), anchorPayload, anchor.occurredAt(), anchor.recordedAt(),
                anchor.previousHash(), anchor.hashVersion());
        return anchorHash.equals(anchor.contentHash());
    }

    private boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
}
