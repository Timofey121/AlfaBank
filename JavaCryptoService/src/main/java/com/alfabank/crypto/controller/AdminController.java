package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.GenerateKeystoreRequest;
import com.alfabank.crypto.dto.response.GenerateKeystoreResponse;
import com.alfabank.crypto.service.KeyGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Служебные операции. Аутентификации нет — только для Dev-стенда.")
public class AdminController {

    private final KeyGenerationService keyGenerationService;

    public AdminController(KeyGenerationService keyGenerationService) {
        this.keyGenerationService = keyGenerationService;
    }

    @PostMapping("/generate-keystore")
    @Operation(summary = "Сгенерировать RSA-2048 ключ и самоподписанный сертификат")
    public ResponseEntity<GenerateKeystoreResponse> generateKeystore(
            @Valid @RequestBody GenerateKeystoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(keyGenerationService.generate(request));
    }
}
