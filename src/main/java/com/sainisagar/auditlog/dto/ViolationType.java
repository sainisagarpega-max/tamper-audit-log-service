package com.sainisagar.auditlog.dto;

public enum ViolationType {
    GENESIS_HASH_MISMATCH,
    SEQUENCE_GAP,
    PREVIOUS_HASH_MISMATCH,
    CONTENT_HASH_MISMATCH,
    UNSUPPORTED_HASH_VERSION,
    CHAIN_HEAD_MISMATCH
}
