package com.alfabank.crypto.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(KeyAliasNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleKeyNotFound(KeyAliasNotFoundException ex) {
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "KEY_ALIAS_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(KeystoreException.class)
    public ResponseEntity<Map<String, Object>> handleKeystore(KeystoreException ex) {
        log.error("Keystore error", ex);
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "KEYSTORE_ERROR", "Keystore operation failed");
    }

    @ExceptionHandler(CryptoOperationException.class)
    public ResponseEntity<Map<String, Object>> handleCrypto(CryptoOperationException ex) {
        log.error("Crypto operation failed", ex);
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "CRYPTO_OPERATION_FAILED", "Crypto operation failed");
    }

    @ExceptionHandler(NetworkException.class)
    public ResponseEntity<Map<String, Object>> handleNetwork(NetworkException ex) {
        log.error("Network error", ex);
        return errorResponse(HttpStatus.BAD_GATEWAY, "NETWORK_ERROR", "Failed to fetch document");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request parameter");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message,
                        "timestamp", Instant.now().toString()
                )
        ));
    }
}
