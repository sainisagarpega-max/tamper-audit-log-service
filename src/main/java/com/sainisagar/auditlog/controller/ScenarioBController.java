package com.sainisagar.auditlog.controller;

import com.sainisagar.auditlog.dto.AuditExportBundle;
import com.sainisagar.auditlog.dto.RedactionRequest;
import com.sainisagar.auditlog.dto.RedactionResponse;
import com.sainisagar.auditlog.dto.RetentionResponse;
import com.sainisagar.auditlog.dto.ExportVerificationResponse;
import com.sainisagar.auditlog.service.ExportService;
import com.sainisagar.auditlog.service.ExportVerificationService;
import com.sainisagar.auditlog.service.RedactionService;
import com.sainisagar.auditlog.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScenarioBController {

    private final RetentionService retentionService;
    private final RedactionService redactionService;
    private final ExportService exportService;
    private final ExportVerificationService exportVerificationService;

    public ScenarioBController(RetentionService retentionService, RedactionService redactionService,
                               ExportService exportService, ExportVerificationService exportVerificationService) {
        this.retentionService = retentionService;
        this.redactionService = redactionService;
        this.exportService = exportService;
        this.exportVerificationService = exportVerificationService;
    }

    @PostMapping("/retention/archive")
    @Operation(summary = "Archive audit events older than the configured retention period")
    public RetentionResponse archiveExpired() {
        return retentionService.archiveExpired();
    }

    @PostMapping("/audit-events/{eventId}/redactions")
    @Operation(summary = "Redact one payload field and append an immutable redaction receipt")
    public RedactionResponse redact(@PathVariable long eventId, @Valid @RequestBody RedactionRequest request) {
        return redactionService.redact(eventId, request);
    }

    @GetMapping("/audit-events/export")
    @Operation(summary = "Export events for one actor or resource with chain metadata")
    public AuditExportBundle export(@RequestParam(required = false) String actorId,
                                    @RequestParam(required = false) String resourceId) {
        return exportService.export(actorId, resourceId);
    }

    @PostMapping("/audit-events/export/verify")
    @Operation(summary = "Verify an exported audit bundle without changing stored data")
    public ExportVerificationResponse verifyExport(@RequestBody AuditExportBundle bundle) {
        return exportVerificationService.verify(bundle);
    }
}
