package com.sainisagar.auditlog.dto;

public record ExportVerificationResponse(
        boolean valid,
        String violation,
        Long sequenceNumber
) {
    public static ExportVerificationResponse verified() {
        return new ExportVerificationResponse(true, null, null);
    }

    public static ExportVerificationResponse invalid(String violation, Long sequenceNumber) {
        return new ExportVerificationResponse(false, violation, sequenceNumber);
    }
}
