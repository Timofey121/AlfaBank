package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record SignResponse(
        String operationId,
        String signature,
        String mode,
        String signerCertificate,
        Instant timestamp
) {}
