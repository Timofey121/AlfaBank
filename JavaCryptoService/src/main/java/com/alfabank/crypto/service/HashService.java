package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.DigestProvider;
import com.alfabank.crypto.dto.request.HashRequest;
import com.alfabank.crypto.dto.response.HashResponse;
import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.model.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;

@Service
public class HashService {

    private static final Logger log = LoggerFactory.getLogger(HashService.class);

    private final DigestProvider digestProvider;
    private final AuditedOperation auditedOperation;

    public HashService(DigestProvider digestProvider, AuditedOperation auditedOperation) {
        this.digestProvider = digestProvider;
        this.auditedOperation = auditedOperation;
    }

    @Transactional
    public HashResponse hash(HashRequest request) {
        return auditedOperation.run(OperationType.HASH, null, null, "HASH_FAILED",
                (msg, cause) -> new CryptoOperationException("Hash failed: " + msg, cause),
                op -> {
                    byte[] data = request.data() != null ? Base64.getDecoder().decode(request.data()) : null;
                    log.info("SHA-256 hash requested, input size {} bytes", data.length);
                    String hash = digestProvider.sha256Hex(data);
                    log.debug("[{}] hash={}", op.getId(), hash);
                    HashResponse response = new HashResponse(op.getId(), "SHA-256", hash, data.length, Instant.now());
                    return AuditedOperation.Outcome.of(response, hash.getBytes());
                });
    }
}
