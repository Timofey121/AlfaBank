package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record FetchResponse(
        String operationId,
        String content,
        String contentType,
        long sizeBytes,
        int httpStatus,
        Instant fetchedAt
) {}
