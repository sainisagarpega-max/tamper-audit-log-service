package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.ChainVerificationResponse;
import com.sainisagar.auditlog.dto.ViolationType;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.entity.ChainState;
import com.sainisagar.auditlog.repository.AuditEventRepository;
import com.sainisagar.auditlog.repository.ChainStateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuditEventService {

    private static final String GLOBAL_CHAIN = "GLOBAL";

    private final AuditEventRepository eventRepository;
    private final ChainStateRepository chainStateRepository;
    private final AuditHashService hashService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditEventService(AuditEventRepository eventRepository, ChainStateRepository chainStateRepository,
                             AuditHashService hashService, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.chainStateRepository = chainStateRepository;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public AuditEventResponse create(AuditEventRequest request) {
        if (!request.payload().isObject()) {
            throw new IllegalArgumentException("'payload' must be a JSON object");
        }
        ChainState state = chainStateRepository.findByNameForUpdate(GLOBAL_CHAIN)
                .orElseThrow(() -> new IllegalStateException("Global chain state is missing"));
        long sequence = state.getLastSequence() + 1;
        Instant recordedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant occurredAt = request.occurredAt() == null
                ? null
                : request.occurredAt().truncatedTo(ChronoUnit.MICROS);
        String payload = hashService.canonicalizePayload(request.payload());
        String contentHash = hashService.calculate(sequence, request.eventType(), request.actorId(),
                request.resourceType(), request.resourceId(), payload, occurredAt, recordedAt,
                state.getLastHash(), AuditHashService.CURRENT_VERSION);

        AuditEvent saved = eventRepository.save(new AuditEvent(sequence, request.eventType(), request.actorId(),
                request.resourceType(), request.resourceId(), payload, occurredAt, recordedAt,
                state.getLastHash(), contentHash, AuditHashService.CURRENT_VERSION));
        state.advance(sequence, contentHash);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> query(String actorId, String resourceType, String resourceId,
                                          String eventType, Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        Specification<AuditEvent> specification = Specification.unrestricted();
        specification = andEqual(specification, "actorId", actorId);
        specification = andEqual(specification, "resourceType", resourceType);
        specification = andEqual(specification, "resourceId", resourceId);
        specification = andEqual(specification, "eventType", eventType);
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("recordedAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("recordedAt"), to));
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        return eventRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ChainVerificationResponse verify() {
        List<AuditEvent> events = eventRepository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        String expectedPreviousHash = AuditHashService.GENESIS_HASH;
        long expectedSequence = 1;
        long checked = 0;

        for (AuditEvent event : events) {
            if (event.getHashVersion() != AuditHashService.CURRENT_VERSION) {
                return ChainVerificationResponse.broken(checked, event.getSequenceNumber(),
                        ViolationType.UNSUPPORTED_HASH_VERSION);
            }
            if (event.getSequenceNumber() != expectedSequence) {
                return ChainVerificationResponse.broken(checked, event.getSequenceNumber(), ViolationType.SEQUENCE_GAP);
            }
            if (expectedSequence == 1 && !AuditHashService.GENESIS_HASH.equals(event.getPreviousHash())) {
                return ChainVerificationResponse.broken(checked, event.getSequenceNumber(),
                        ViolationType.GENESIS_HASH_MISMATCH);
            }
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return ChainVerificationResponse.broken(checked, event.getSequenceNumber(),
                        ViolationType.PREVIOUS_HASH_MISMATCH);
            }
            if (!hashService.recalculate(event).equals(event.getContentHash())) {
                return ChainVerificationResponse.broken(checked, event.getSequenceNumber(),
                        ViolationType.CONTENT_HASH_MISMATCH);
            }
            checked++;
            expectedSequence++;
            expectedPreviousHash = event.getContentHash();
        }
        return ChainVerificationResponse.intact(checked);
    }

    private Specification<AuditEvent> andEqual(Specification<AuditEvent> current, String field, String value) {
        if (value == null || value.isBlank()) {
            return current;
        }
        return current.and((root, query, builder) -> builder.equal(root.get(field), value));
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        try {
            return new AuditEventResponse(event.getId(), event.getSequenceNumber(), event.getEventType(),
                    event.getActorId(), event.getResourceType(), event.getResourceId(),
                    objectMapper.readTree(event.getPayload()), event.getOccurredAt(), event.getRecordedAt(),
                    event.getPreviousHash(), event.getContentHash(), event.getHashVersion());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored payload is invalid", exception);
        }
    }
}
