package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.entity.RedactionReceipt;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
public class RedactionProofService {

    private final AuditHashService hashService;

    public RedactionProofService(AuditHashService hashService) {
        this.hashService = hashService;
    }

    public String receiptHash(long targetSequence, String originalContentHash, String jsonPointer,
                              String saltedValueHash, String redactedPayloadHash, String requestedBy,
                              String reason, String redactedAt) {
        String canonical = new StringJoiner("|")
                .add("redaction-v1")
                .add(Long.toString(targetSequence))
                .add(originalContentHash)
                .add(jsonPointer)
                .add(saltedValueHash)
                .add(redactedPayloadHash)
                .add(requestedBy)
                .add(reason)
                .add(redactedAt)
                .toString();
        return hashService.digestUtf8(canonical);
    }

    public boolean isValid(RedactionReceipt receipt) {
        return receipt.getReceiptHash().equals(receiptHash(receipt.getTargetSequence(),
                receipt.getOriginalContentHash(), receipt.getJsonPointer(), receipt.getSaltedValueHash(),
                receipt.getRedactedPayloadHash(), receipt.getRequestedBy(), receipt.getReason(),
                receipt.getRedactedAt().toString()));
    }
}
