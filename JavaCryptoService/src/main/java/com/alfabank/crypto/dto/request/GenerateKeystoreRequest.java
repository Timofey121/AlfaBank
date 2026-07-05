package com.alfabank.crypto.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GenerateKeystoreRequest(
        @NotBlank String alias,
        @NotBlank String cn,
        @Min(1) @Max(3650) int validityDays
) {}
