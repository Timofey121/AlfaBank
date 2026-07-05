package com.alfabank.crypto.controller;

import com.alfabank.crypto.dto.response.SignResponse;
import com.alfabank.crypto.dto.response.VerifyResponse;
import com.alfabank.crypto.exception.KeyAliasNotFoundException;
import com.alfabank.crypto.service.SignatureService;
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

@WebMvcTest(SignatureController.class)
class SignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignatureService signatureService;

    private static final String SIGN_BODY = """
            {
              "data": "SGVsbG8gV29ybGQ=",
              "keyAlias": "crypto-key",
              "mode": "ATTACHED"
            }
            """;

    @Test
    void sign_validRequest_returns200() throws Exception {
        SignResponse response = new SignResponse("op-2", "c2lnbmF0dXJl", "ATTACHED", "Y2VydA==", Instant.now());
        when(signatureService.sign(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("op-2"))
                .andExpect(jsonPath("$.mode").value("ATTACHED"))
                .andExpect(jsonPath("$.signature").exists());
    }

    @Test
    void sign_unknownAlias_returns422() throws Exception {
        when(signatureService.sign(any())).thenThrow(new KeyAliasNotFoundException("no-such-key"));

        mockMvc.perform(post("/api/v1/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGN_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KEY_ALIAS_NOT_FOUND"));
    }

    @Test
    void sign_invalidMode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"SGVsbG8=","keyAlias":"k","mode":"WRONG"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_validSignature_returnsValidTrue() throws Exception {
        VerifyResponse response = new VerifyResponse("op-3", true,
                "CN=Test", "1a2b", Instant.now(), Instant.now().plusSeconds(86400), Instant.now());
        when(signatureService.verify(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/crypto/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signature\":\"c2lnbmF0dXJl\",\"mode\":\"ATTACHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.signerSubject").value("CN=Test"));
    }
}
