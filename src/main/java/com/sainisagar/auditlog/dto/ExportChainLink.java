package com.sainisagar.auditlog.dto;

public record ExportChainLink(
        Long sequenceNumber,
        String previousHash,
        String contentHash,
        Integer hashVersion
) {
}
