package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.response.OperationDetailResponse;
import com.alfabank.crypto.dto.response.OperationRecordResponse;
import com.alfabank.crypto.service.OperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations")
@Tag(name = "Operations", description = "Audit log of all crypto operations")
public class OperationsController {

    private final OperationsService operationsService;

    public OperationsController(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    @Operation(summary = "List all operations with pagination")
    public ResponseEntity<Page<OperationRecordResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(operationsService.list(page, size, type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get operation by ID, including type-specific detail data")
    public ResponseEntity<OperationDetailResponse> getById(@PathVariable String id) {
        return operationsService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
