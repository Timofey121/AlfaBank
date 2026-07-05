package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record VerifyResponse(
        String operationId,
        boolean valid,
        String signerSubject,
        String signerSerial,
        Instant certNotBefore,
        Instant certNotAfter,
        Instant timestamp
) {}
