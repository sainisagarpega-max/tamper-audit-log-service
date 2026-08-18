package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.AuditExportBundle;
import com.sainisagar.auditlog.dto.ExportChainLink;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.repository.AuditEventRepository;
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

@Service
public class ExportService {

    private final AuditEventRepository repository;
    private final AuditEventService eventService;
    private final AuditHashService hashService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public ExportService(AuditEventRepository repository, AuditEventService eventService,
                         AuditHashService hashService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.eventService = eventService;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
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
        List<ExportChainLink> links = fullChain.stream()
                .map(event -> new ExportChainLink(event.getSequenceNumber(), event.getPreviousHash(),
                        event.getContentHash(), event.getHashVersion()))
                .toList();
        String chainHead = fullChain.isEmpty()
                ? AuditHashService.GENESIS_HASH
                : fullChain.getLast().getContentHash();
        Instant exportedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> unsignedBundle = new LinkedHashMap<>();
        unsignedBundle.put("bundleVersion", "1");
        unsignedBundle.put("exportedAt", exportedAt);
        unsignedBundle.put("actorId", actorId);
        unsignedBundle.put("resourceId", resourceId);
        unsignedBundle.put("hashAlgorithm", "SHA-256");
        unsignedBundle.put("genesisHash", AuditHashService.GENESIS_HASH);
        unsignedBundle.put("records", records);
        unsignedBundle.put("chainMetadata", links);
        unsignedBundle.put("chainHeadHash", chainHead);
        String canonicalBundle = hashService.canonicalizePayload(objectMapper.valueToTree(unsignedBundle));
        String bundleHash = hashService.digestUtf8(canonicalBundle);

        return new AuditExportBundle("1", exportedAt, actorId, resourceId, "SHA-256",
                AuditHashService.GENESIS_HASH, records, links, chainHead, bundleHash);
    }
}
