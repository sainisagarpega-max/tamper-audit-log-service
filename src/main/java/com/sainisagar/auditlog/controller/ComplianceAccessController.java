package com.sainisagar.auditlog.controller;

import com.sainisagar.auditlog.dto.ClientAccountAccessRequest;
import com.sainisagar.auditlog.dto.ClientAccountAccessResponse;
import com.sainisagar.auditlog.service.ComplianceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/compliance/account-access")
public class ComplianceAccessController {

    private final ComplianceAccessService service;

    public ComplianceAccessController(ComplianceAccessService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an application-mediated client-account access attempt")
    public ClientAccountAccessResponse record(@Valid @RequestBody ClientAccountAccessRequest request) {
        return service.record(request);
    }

    @GetMapping
    @Operation(summary = "Report client-account access events for compliance review")
    public Page<ClientAccountAccessResponse> report(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return service.report(accountId, actorId, from, to, page, size, authentication.getName());
    }
}
