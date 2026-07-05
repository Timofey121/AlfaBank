package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.HashRequest;
import com.alfabank.crypto.dto.response.HashResponse;
import com.alfabank.crypto.service.HashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crypto")
@Tag(name = "Hash", description = "SHA-256 hash computation")
public class HashController {

    private final HashService hashService;

    public HashController(HashService hashService) {
        this.hashService = hashService;
    }

    @PostMapping("/hash")
    @Operation(summary = "Compute SHA-256 hash of data")
    public ResponseEntity<HashResponse> hash(@Valid @RequestBody HashRequest request) {
        return ResponseEntity.ok(hashService.hash(request));
    }
}
