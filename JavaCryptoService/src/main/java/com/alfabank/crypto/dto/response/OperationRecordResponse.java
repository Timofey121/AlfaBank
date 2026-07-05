package com.alfabank.crypto.dto.response;

import com.alfabank.crypto.model.CryptoOperation;

import java.time.Instant;

public record OperationRecordResponse(
        String id,
        String type,
        String status,
        Instant createdAt,
        String keyAlias
) {

    public static OperationRecordResponse from(CryptoOperation op) {
        return new OperationRecordResponse(
                op.getId(),
                op.getType(),
                op.getStatus(),
                op.getCreatedAt(),
                op.getKeyAlias()
        );
    }
}
