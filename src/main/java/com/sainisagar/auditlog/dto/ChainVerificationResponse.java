package com.sainisagar.auditlog.dto;

public record ChainVerificationResponse(
        boolean intact,
        long recordsChecked,
        Long firstBrokenSequence,
        ViolationType violationType
) {
    public static ChainVerificationResponse intact(long recordsChecked) {
        return new ChainVerificationResponse(true, recordsChecked, null, null);
    }

    public static ChainVerificationResponse broken(long recordsChecked, long sequence, ViolationType type) {
        return new ChainVerificationResponse(false, recordsChecked, sequence, type);
    }
}
