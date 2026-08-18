package com.sainisagar.auditlog.controller;

import com.sainisagar.auditlog.dto.AuditEventRequest;
import com.sainisagar.auditlog.dto.AuditEventResponse;
import com.sainisagar.auditlog.dto.ChainVerificationResponse;
import com.sainisagar.auditlog.service.AuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private final AuditEventService service;

    public AuditEventController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append an immutable audit event")
    public AuditEventResponse create(@Valid @RequestBody AuditEventRequest request) {
        return service.create(request);
    }

    @GetMapping
    @Operation(summary = "Query audit events using optional filters")
    public Page<AuditEventResponse> query(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.query(actorId, resourceType, resourceId, eventType, from, to, page, size, includeArchived);
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify the complete audit hash chain")
    public ChainVerificationResponse verify() {
        return service.verify();
    }
}
