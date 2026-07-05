package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to sign a document with PKCS#7")
public record SignRequest(
        @NotBlank(message = "data is required")
        @Size(max = 10_000_000)
        @Schema(description = "Base64-encoded document to sign", example = "SGVsbG8gV29ybGQ=")
        String data,

        @NotBlank(message = "keyAlias is required")
        @Schema(description = "Key alias in keystore", example = "crypto-key")
        String keyAlias,

        @Pattern(regexp = "ATTACHED|DETACHED", message = "mode must be ATTACHED or DETACHED")
        @Schema(description = "Signature mode", example = "ATTACHED")
        String mode
) {
    public SignRequest {
        if (mode == null) mode = "ATTACHED";
    }
}
