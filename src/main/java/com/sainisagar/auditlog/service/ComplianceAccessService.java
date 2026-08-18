package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.AccessChannel;
import com.sainisagar.auditlog.dto.AccessOutcome;
import com.sainisagar.auditlog.dto.AccountAccessAction;
import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.ClientAccountAccessRequest;
import com.sainisagar.auditlog.dto.ClientAccountAccessResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

@Service
public class ComplianceAccessService {

    public static final String EVENT_TYPE = "CLIENT_ACCOUNT_ACCESS";
    public static final String RESOURCE_TYPE = "CLIENT_ACCOUNT";

    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;

    public ComplianceAccessService(AuditEventService auditEventService, ObjectMapper objectMapper) {
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
    }

    public ClientAccountAccessResponse record(ClientAccountAccessRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", request.action().name());
        payload.put("purpose", request.purpose());
        payload.put("channel", request.channel().name());
        payload.put("outcome", request.outcome().name());
        payload.put("correlationId", request.correlationId());

        AuditEventResponse event = auditEventService.create(new AuditEventRequest(EVENT_TYPE, request.actorId(),
                RESOURCE_TYPE, request.accountId(), payload, request.occurredAt()));
        return toComplianceResponse(event);
    }

    public Page<ClientAccountAccessResponse> report(String accountId, String actorId, Instant from, Instant to,
                                                     int page, int size, String requestedBy) {
        Page<AuditEventResponse> result = auditEventService.query(actorId, RESOURCE_TYPE, accountId,
                EVENT_TYPE, from, to, page, size, true);
        auditReportAccess(accountId, actorId, from, to, requestedBy, result.getTotalElements());
        return result.map(this::toComplianceResponse);
    }

    private void auditReportAccess(String accountId, String actorId, Instant from, Instant to,
                                   String requestedBy, long resultCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        putNullable(payload, "accountIdFilter", accountId);
        putNullable(payload, "actorIdFilter", actorId);
        putNullable(payload, "from", from == null ? null : from.toString());
        putNullable(payload, "to", to == null ? null : to.toString());
        payload.put("resultCount", resultCount);
        auditEventService.create(new AuditEventRequest("COMPLIANCE_REPORT_ACCESSED", requestedBy,
                "COMPLIANCE_REPORT", "CLIENT_ACCOUNT_ACCESS", payload, null));
    }

    private ClientAccountAccessResponse toComplianceResponse(AuditEventResponse event) {
        JsonNode payload = event.payload();
        return new ClientAccountAccessResponse(event.id(), event.sequenceNumber(), event.resourceId(), event.actorId(),
                AccountAccessAction.valueOf(payload.path("action").asString()),
                payload.path("purpose").asString(),
                AccessChannel.valueOf(payload.path("channel").asString()),
                AccessOutcome.valueOf(payload.path("outcome").asString()),
                payload.path("correlationId").asString(), event.occurredAt(), event.recordedAt());
    }

    private void putNullable(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }
}
