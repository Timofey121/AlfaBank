package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to encrypt data with RSA+AES-GCM")
public record EncryptRequest(
        @NotBlank(message = "plaintext is required")
        @Size(max = 10_000_000, message = "plaintext exceeds maximum allowed size")
        @Schema(description = "Base64-encoded plaintext to encrypt")
        String plaintext,

        @NotBlank(message = "recipientCertificate is required")
        @Size(max = 100_000, message = "recipientCertificate exceeds maximum allowed size")
        @Schema(description = "Base64-encoded DER recipient certificate")
        String recipientCertificate,

        @Size(max = 255, message = "filename exceeds maximum allowed length")
        @Schema(description = "Original filename, preserved through decrypt so the downloaded file keeps its name/extension", example = "invoice.pdf")
        String filename
) {}
