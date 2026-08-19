package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.AuditExportBundle;
import com.sainisagar.auditlog.dto.ExportChainLink;
import com.sainisagar.auditlog.dto.ExportRedactionProof;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.entity.RedactionReceipt;
import com.sainisagar.auditlog.repository.AuditEventRepository;
import com.sainisagar.auditlog.repository.RedactionReceiptRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final AuditEventRepository repository;
    private final AuditEventService eventService;
    private final AuditHashService hashService;
    private final ObjectMapper objectMapper;
    private final RedactionReceiptRepository receiptRepository;
    private final Clock clock = Clock.systemUTC();

    public ExportService(AuditEventRepository repository, AuditEventService eventService,
                         AuditHashService hashService, ObjectMapper objectMapper,
                         RedactionReceiptRepository receiptRepository) {
        this.repository = repository;
        this.eventService = eventService;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public AuditExportBundle export(String actorId, String resourceId) {
        boolean hasActor = actorId != null && !actorId.isBlank();
        boolean hasResource = resourceId != null && !resourceId.isBlank();
        if (hasActor == hasResource) {
            throw new IllegalArgumentException("Provide exactly one of actorId or resourceId");
        }

        List<AuditEvent> matching = hasActor
                ? repository.findByActorIdOrderBySequenceNumberAsc(actorId)
                : repository.findByResourceIdOrderBySequenceNumberAsc(resourceId);
        List<AuditEvent> fullChain = repository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        List<AuditEventResponse> records = matching.stream().map(eventService::toResponse).toList();
        Set<Long> matchingSequences = matching.stream().map(AuditEvent::getSequenceNumber).collect(Collectors.toSet());
        Map<String, AuditEvent> anchorsByReceiptHash = fullChain.stream()
                .filter(event -> "PAYLOAD_REDACTED".equals(event.getEventType()))
                .filter(event -> readReceiptHash(event) != null)
                .collect(Collectors.toMap(this::readReceiptHash, Function.identity(), (first, second) -> second));
        List<ExportRedactionProof> redactionProofs = receiptRepository.findAllByOrderByRedactedAtAsc().stream()
                .filter(receipt -> matchingSequences.contains(receipt.getTargetSequence()))
                .map(receipt -> toProof(receipt, anchorsByReceiptHash.get(receipt.getReceiptHash())))
                .toList();
        List<ExportChainLink> links = fullChain.stream()
                .map(event -> new ExportChainLink(event.getSequenceNumber(), event.getPreviousHash(),
                        event.getContentHash(), event.getHashVersion()))
                .toList();
        String chainHead = fullChain.isEmpty()
                ? AuditHashService.GENESIS_HASH
                : fullChain.getLast().getContentHash();
        Instant exportedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        AuditExportBundle unsignedBundle = new AuditExportBundle("2", exportedAt, actorId, resourceId,
                "SHA-256", AuditHashService.GENESIS_HASH, records, redactionProofs, links, chainHead, null);
        String bundleHash = calculateBundleHash(unsignedBundle);

        return new AuditExportBundle("2", exportedAt, actorId, resourceId, "SHA-256",
                AuditHashService.GENESIS_HASH, records, redactionProofs, links, chainHead, bundleHash);
    }

    String calculateBundleHash(AuditExportBundle bundle) {
        Map<String, Object> unsignedBundle = new LinkedHashMap<>();
        unsignedBundle.put("bundleVersion", bundle.bundleVersion());
        unsignedBundle.put("exportedAt", bundle.exportedAt());
        unsignedBundle.put("actorId", bundle.actorId());
        unsignedBundle.put("resourceId", bundle.resourceId());
        unsignedBundle.put("hashAlgorithm", bundle.hashAlgorithm());
        unsignedBundle.put("genesisHash", bundle.genesisHash());
        unsignedBundle.put("records", bundle.records());
        unsignedBundle.put("redactionProofs", bundle.redactionProofs());
        unsignedBundle.put("chainMetadata", bundle.chainMetadata());
        unsignedBundle.put("chainHeadHash", bundle.chainHeadHash());
        String canonicalBundle = hashService.canonicalizePayload(objectMapper.valueToTree(unsignedBundle));
        return hashService.digestUtf8(canonicalBundle);
    }

    private ExportRedactionProof toProof(RedactionReceipt receipt, AuditEvent anchor) {
        if (anchor == null) {
            throw new IllegalStateException("Redaction receipt is not anchored in the audit chain: "
                    + receipt.getReceiptHash());
        }
        return new ExportRedactionProof(receipt.getTargetSequence(), receipt.getOriginalContentHash(),
                receipt.getJsonPointer(), receipt.getSaltedValueHash(), receipt.getRedactedPayloadHash(),
                receipt.getRequestedBy(), receipt.getReason(), receipt.getRedactedAt(), receipt.getReceiptHash(),
                eventService.toResponse(anchor));
    }

    private String readReceiptHash(AuditEvent event) {
        return objectMapper.readTree(event.getPayload()).path("receiptHash").asString(null);
    }
}
