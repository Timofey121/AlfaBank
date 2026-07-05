package com.alfabank.crypto.dto.response;

import java.time.Instant;

public record GenerateKeystoreResponse(
        String alias,
        String subject,
        String serialNumber,
        Instant notBefore,
        Instant notAfter,
        String certBase64
) {}
