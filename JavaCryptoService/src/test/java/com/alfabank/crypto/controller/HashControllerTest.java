package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.response.HashResponse;
import com.alfabank.crypto.service.HashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HashController.class)
class HashControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HashService hashService;

    @Test
    void hash_validRequest_returns200() throws Exception {
        HashResponse response = new HashResponse("op-1", "SHA-256",
                "185f8db32921bd46d35cc2f2f629e3e7d9b48b8b75e3e4f5c3e4c58e3e4f5c3", 5, Instant.now());
        when(hashService.hash(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/crypto/hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"SGVsbG8=\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("SHA-256"))
                .andExpect(jsonPath("$.hash").exists())
                .andExpect(jsonPath("$.operationId").value("op-1"));
    }

    @Test
    void hash_missingData_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/crypto/hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hash_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/crypto/hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
