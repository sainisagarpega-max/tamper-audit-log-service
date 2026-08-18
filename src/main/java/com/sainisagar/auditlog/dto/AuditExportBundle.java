package com.sainisagar.auditlog.dto;

import java.time.Instant;
import java.util.List;

public record AuditExportBundle(
        String bundleVersion,
        Instant exportedAt,
        String actorId,
        String resourceId,
        String hashAlgorithm,
        String genesisHash,
        List<AuditEventResponse> records,
        List<ExportChainLink> chainMetadata,
        String chainHeadHash,
        String bundleHash
) {
}
