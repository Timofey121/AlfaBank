package com.alfabank.crypto.dto.response;

import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.model.EncryptionDetail;
import com.alfabank.crypto.model.FetchDetail;
import com.alfabank.crypto.model.SignatureDetail;

import java.time.Instant;

public record OperationDetailResponse(
        String id,
        String type,
        String status,
        String keyAlias,
        String inputHash,
        String outputHash,
        String errorCode,
        String errorMsg,
        Long durationMs,
        Instant createdAt,
        SignatureInfo signature,
        EncryptionInfo encryption,
        FetchInfo fetch
) {

    public static OperationDetailResponse from(
            CryptoOperation op,
            SignatureInfo signature,
            EncryptionInfo encryption,
            FetchInfo fetch
    ) {
        return new OperationDetailResponse(
                op.getId(),
                op.getType(),
                op.getStatus(),
                op.getKeyAlias(),
                op.getInputHash(),
                op.getOutputHash(),
                op.getErrorCode(),
                op.getErrorMsg(),
                op.getDurationMs(),
                op.getCreatedAt(),
                signature,
                encryption,
                fetch
        );
    }

    public record SignatureInfo(
            String signMode,
            String signerSubject,
            String signerSerial,
            Instant certNotBefore,
            Instant certNotAfter,
            Boolean isValid
    ) {
        public static SignatureInfo from(SignatureDetail d) {
            return new SignatureInfo(
                    d.getSignMode(),
                    d.getSignerSubject(),
                    d.getSignerSerial(),
                    d.getCertNotBefore(),
                    d.getCertNotAfter(),
                    d.getIsValid()
            );
        }
    }

    public record EncryptionInfo(
            String algorithm,
            String recipientDn,
            Integer inputSize,
            Integer outputSize,
            String originalFilename
    ) {
        public static EncryptionInfo from(EncryptionDetail d) {
            return new EncryptionInfo(
                    d.getAlgorithm(),
                    d.getRecipientDn(),
                    d.getInputSize(),
                    d.getOutputSize(),
                    d.getOriginalFilename()
            );
        }
    }

    public record FetchInfo(
            String sourceUrl,
            Integer httpStatus,
            String contentType,
            Long sizeBytes
    ) {
        public static FetchInfo from(FetchDetail d) {
            return new FetchInfo(
                    d.getSourceUrl(),
                    d.getHttpStatus(),
                    d.getContentType(),
                    d.getSizeBytes()
            );
        }
    }
}
