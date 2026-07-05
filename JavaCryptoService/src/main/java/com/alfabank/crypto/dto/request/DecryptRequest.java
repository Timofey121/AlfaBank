package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to decrypt CMS EnvelopedData")
public record DecryptRequest(
        @NotBlank(message = "ciphertext is required")
        @Size(max = 10_000_000)
        @Schema(description = "Base64-encoded CMS EnvelopedData")
        String ciphertext,

        @NotBlank(message = "keyAlias is required")
        @Schema(description = "Private key alias in keystore")
        String keyAlias
) {}
