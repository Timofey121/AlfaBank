package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.SignRequest;
import com.alfabank.crypto.dto.request.VerifyRequest;
import com.alfabank.crypto.dto.response.SignResponse;
import com.alfabank.crypto.dto.response.VerifyResponse;
import com.alfabank.crypto.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crypto")
@Tag(name = "Signature", description = "PKCS#7 digital signature operations")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping("/sign")
    @Operation(summary = "Sign document with PKCS#7 (attached or detached)")
    public ResponseEntity<SignResponse> sign(@Valid @RequestBody SignRequest request) {
        return ResponseEntity.ok(signatureService.sign(request));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify PKCS#7 signature")
    public ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(signatureService.verify(request));
    }
}
