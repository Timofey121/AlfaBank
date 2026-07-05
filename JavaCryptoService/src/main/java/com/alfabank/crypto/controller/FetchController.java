package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.FetchRequest;
import com.alfabank.crypto.dto.response.FetchResponse;
import com.alfabank.crypto.service.FetchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fetch")
@Tag(name = "Fetch", description = "Secure HTTPS document fetching")
public class FetchController {

    private final FetchService fetchService;

    public FetchController(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @PostMapping("/document")
    @Operation(summary = "Fetch document from external HTTPS URL")
    public ResponseEntity<FetchResponse> fetchDocument(@Valid @RequestBody FetchRequest request) {
        return ResponseEntity.ok(fetchService.fetch(request));
    }
}
