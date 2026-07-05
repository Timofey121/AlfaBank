package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.request.DecodeRequest;
import com.alfabank.crypto.dto.response.EncodeResponse;
import com.alfabank.crypto.service.ConvertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/convert")
@Tag(name = "Convert", description = "Вспомогательные операции для Web UI: файл/текст ↔ base64")
public class ConvertController {

    private final ConvertService convertService;

    public ConvertController(ConvertService convertService) {
        this.convertService = convertService;
    }

    @PostMapping(value = "/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Закодировать загруженный файл или текст в base64 для передачи в crypto-эндпоинты")
    public ResponseEntity<EncodeResponse> encode(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String text) {
        return ResponseEntity.ok(convertService.encode(file, text));
    }

    @PostMapping("/decode")
    @Operation(summary = "Декодировать base64-результат обратно в файл для скачивания")
    public ResponseEntity<byte[]> decode(@Valid @RequestBody DecodeRequest request) {
        byte[] bytes = convertService.decode(request.base64());
        String safeFilename = convertService.sanitizeFilename(request.filename());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename).build().toString())
                .body(bytes);
    }
}
