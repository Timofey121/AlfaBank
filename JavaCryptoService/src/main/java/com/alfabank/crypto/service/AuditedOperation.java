package com.alfabank.crypto.service;

import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.model.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Component
public class AuditedOperation {

    private static final Logger log = LoggerFactory.getLogger(AuditedOperation.class);

    private final AuditService auditService;

    public AuditedOperation(AuditService auditService) {
        this.auditService = auditService;
    }

    @FunctionalInterface
    public interface Action<T> {
        Outcome<T> execute(CryptoOperation op) throws Exception;
    }

    public record Outcome<T>(T value, byte[] auditOutput) {
        public static <T> Outcome<T> of(T value, byte[] auditOutput) {
            return new Outcome<>(value, auditOutput);
        }

        public static <T> Outcome<T> of(T value) {
            return new Outcome<>(value, null);
        }
    }

    public <T> T run(OperationType type, byte[] inputData, String keyAlias, String failureCode,
                      BiFunction<String, Throwable, ? extends RuntimeException> onUnexpectedError,
                      Action<T> action) {
        long startMs = System.currentTimeMillis();
        CryptoOperation op = auditService.createPending(type.name(), inputData, keyAlias);
        try {
            Outcome<T> outcome = action.execute(op);
            auditService.markSuccess(op, outcome.auditOutput(), startMs);
            return outcome.value();
        } catch (RuntimeException e) {
            log.error("[{}] {} failed: {}", op.getId(), type, e.getMessage());
            auditService.markFailed(op, failureCode, e.getMessage(), startMs);
            throw e;
        } catch (Exception e) {
            log.error("[{}] {} failed: {}", op.getId(), type, e.getMessage());
            auditService.markFailed(op, failureCode, e.getMessage(), startMs);
            throw onUnexpectedError.apply(e.getMessage(), e);
        }
    }
}
