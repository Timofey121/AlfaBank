package com.alfabank.crypto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to fetch a document over HTTPS")
public record FetchRequest(
        @NotBlank(message = "url is required")
        @Schema(description = "HTTPS URL to fetch", example = "https://httpbin.org/json")
        String url,

        @Min(value = 1, message = "timeoutSeconds must be at least 1")
        @Max(value = 120, message = "timeoutSeconds must be at most 120")
        @Schema(description = "Timeout in seconds", defaultValue = "30")
        Integer timeoutSeconds
) {
    public FetchRequest {
        if (timeoutSeconds == null) timeoutSeconds = 30;
    }
}
