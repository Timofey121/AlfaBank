package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record HashResponse(
        String operationId,
        String algorithm,
        String hash,
        int inputSizeBytes,
        Instant timestamp
) {}
