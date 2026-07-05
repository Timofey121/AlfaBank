package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.DecryptRequest;
import com.alfabank.crypto.dto.request.EncryptRequest;
import com.alfabank.crypto.dto.response.DecryptResponse;
import com.alfabank.crypto.dto.response.EncryptResponse;
import com.alfabank.crypto.service.EncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crypto")
@Tag(name = "Encryption", description = "RSA+AES-GCM hybrid encryption operations")
public class EncryptionController {

    private final EncryptionService encryptionService;

    public EncryptionController(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @PostMapping("/encrypt")
    @Operation(summary = "Encrypt data with RSA+AES-GCM (CMS EnvelopedData)")
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        return ResponseEntity.ok(encryptionService.encrypt(request));
    }

    @PostMapping("/decrypt")
    @Operation(summary = "Decrypt CMS EnvelopedData with private key from keystore")
    public ResponseEntity<DecryptResponse> decrypt(@Valid @RequestBody DecryptRequest request) {
        return ResponseEntity.ok(encryptionService.decrypt(request));
    }
}
