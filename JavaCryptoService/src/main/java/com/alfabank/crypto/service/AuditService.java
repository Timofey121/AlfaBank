package com.alfabank.crypto.service;

import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.repository.CryptoOperationRepository;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final CryptoOperationRepository repository;

    public AuditService(CryptoOperationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CryptoOperation createPending(String type, byte[] inputData, String keyAlias) {
        CryptoOperation op = new CryptoOperation();
        op.setId(UUID.randomUUID().toString());
        op.setType(type);
        op.setStatus("PENDING");
        op.setAppSource("JAVA");
        op.setKeyAlias(keyAlias);
        op.setCreatedAt(Instant.now());
        if (inputData != null) op.setInputHash(sha256Hex(inputData));
        CryptoOperation saved = repository.save(op);
        log.debug("[{}] {} started, inputSize={} bytes", saved.getId(), type,
                inputData != null ? inputData.length : 0);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(CryptoOperation op, byte[] outputData, long startMs) {
        op.setStatus("SUCCESS");
        long duration = System.currentTimeMillis() - startMs;
        op.setDurationMs(duration);
        if (outputData != null) op.setOutputHash(sha256Hex(outputData));
        repository.save(op);
        log.debug("[{}] {} completed in {}ms", op.getId(), op.getType(), duration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(CryptoOperation op, String errorCode, String errorMsg, long startMs) {
        op.setStatus("FAILED");
        op.setErrorCode(errorCode);
        op.setErrorMsg(errorMsg);
        op.setDurationMs(System.currentTimeMillis() - startMs);
        repository.save(op);
        log.warn("[{}] {} failed — {}: {}", op.getId(), op.getType(), errorCode, errorMsg);
    }

    private String sha256Hex(byte[] data) {
        try {
            return Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            log.warn("Failed to compute SHA-256 hash", e);
            return null;
        }
    }
}
