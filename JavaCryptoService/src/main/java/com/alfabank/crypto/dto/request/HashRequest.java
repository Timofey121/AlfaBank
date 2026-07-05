package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to compute SHA-256 hash")
public record HashRequest(
        @NotBlank(message = "data is required")
        @Size(max = 10_000_000)
        @Schema(description = "Base64-encoded data to hash")
        String data
) {}
