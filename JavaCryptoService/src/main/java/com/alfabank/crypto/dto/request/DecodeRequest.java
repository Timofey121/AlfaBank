package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to decode base64 back into a downloadable file")
public record DecodeRequest(
        @NotBlank(message = "base64 is required")
        @Size(max = 10_000_000)
        @Schema(description = "Base64-encoded content to decode")
        String base64,

        @Size(max = 255)
        @Schema(description = "Filename to use for the downloaded file", example = "result.bin")
        String filename
) {
    public DecodeRequest {
        if (filename == null || filename.isBlank()) filename = "result.bin";
    }
}
