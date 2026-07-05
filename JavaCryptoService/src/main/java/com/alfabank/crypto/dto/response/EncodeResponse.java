package com.alfabank.crypto.dto.response;

public record EncodeResponse(
        String base64,
        int sizeBytes,
        String filename
) {}
