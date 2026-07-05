package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to verify a PKCS#7 signature")
public record VerifyRequest(
        @NotBlank(message = "signature is required")
        @Schema(description = "Base64-encoded PKCS#7 signature")
        String signature,

        @Schema(description = "Base64-encoded original document (required for DETACHED mode)")
        String data,

        @Pattern(regexp = "ATTACHED|DETACHED", message = "mode must be ATTACHED or DETACHED")
        @Schema(description = "Signature mode", example = "ATTACHED")
        String mode
) {
    public VerifyRequest {
        if (mode == null) mode = "ATTACHED";
    }
}
