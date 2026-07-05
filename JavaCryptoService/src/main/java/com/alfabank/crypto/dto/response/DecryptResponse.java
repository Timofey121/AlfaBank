package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record DecryptResponse(
        String operationId,
        String plaintext,
        String filename,
        Instant timestamp
) {}
