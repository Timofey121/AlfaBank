package com.alfabank.crypto.service;

import com.alfabank.crypto.dto.response.EncodeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class ConvertService {

    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);

    public EncodeResponse encode(MultipartFile file, String text) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = text != null && !text.isBlank();

        if (hasFile == hasText) {
            throw new IllegalArgumentException("Provide either 'file' or 'text', not both/neither");
        }

        byte[] bytes;
        String filename = null;
        if (hasFile) {
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
            }
            filename = file.getOriginalFilename();
        } else {
            bytes = text.getBytes(StandardCharsets.UTF_8);
        }

        log.info("Encoded {} bytes to base64 (filename={})", bytes.length, filename);
        return new EncodeResponse(Base64.getEncoder().encodeToString(bytes), bytes.length, filename);
    }

    public byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 content: " + e.getMessage());
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "result.bin";
        }
        String cleaned = filename.replace("/", "").replace("\\", "").replace("..", "");
        try {
            Path path = Paths.get(cleaned);
            String baseName = path.getFileName() != null ? path.getFileName().toString() : "";
            if (baseName.isBlank()) {
                return "result.bin";
            }
            return baseName;
        } catch (Exception e) {
            return "result.bin";
        }
    }
}
