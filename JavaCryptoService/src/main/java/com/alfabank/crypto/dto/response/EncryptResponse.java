package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record EncryptResponse(
        String operationId,
        String ciphertext,
        Instant timestamp
) {}
