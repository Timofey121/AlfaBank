package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.DigestProvider;
import com.alfabank.crypto.dto.request.HashRequest;
import com.alfabank.crypto.dto.response.HashResponse;
import com.alfabank.crypto.model.CryptoOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HashServiceTest {

    @Mock private DigestProvider digestProvider;
    @Mock private AuditService auditService;
    private HashService hashService;

    private CryptoOperation pendingOp;

    @BeforeEach
    void setUp() {
        hashService = new HashService(digestProvider, new AuditedOperation(auditService));
        pendingOp = new CryptoOperation();
        pendingOp.setId("test-op-id");
        pendingOp.setType("HASH");
        when(auditService.createPending(eq("HASH"), any(), isNull())).thenReturn(pendingOp);
    }

    @Test
    void hash_returnsCorrectAlgorithm() {
        when(digestProvider.sha256Hex(any())).thenReturn("abc123");
        String data = Base64.getEncoder().encodeToString("hello".getBytes());

        HashResponse response = hashService.hash(new HashRequest(data));

        assertEquals("SHA-256", response.algorithm());
        assertEquals("abc123", response.hash());
        assertEquals("test-op-id", response.operationId());
    }

    @Test
    void hash_callsAuditSuccess_onNormalFlow() {
        when(digestProvider.sha256Hex(any())).thenReturn("deadbeef");
        String data = Base64.getEncoder().encodeToString("test".getBytes());

        hashService.hash(new HashRequest(data));

        verify(auditService).markSuccess(eq(pendingOp), any(), anyLong());
        verify(auditService, never()).markFailed(any(), any(), any(), anyLong());
    }

    @Test
    void hash_callsAuditFailed_whenDigestThrows() {
        when(digestProvider.sha256Hex(any())).thenThrow(new RuntimeException("BC error"));
        String data = Base64.getEncoder().encodeToString("test".getBytes());

        assertThrows(RuntimeException.class, () -> hashService.hash(new HashRequest(data)));
        verify(auditService).markFailed(eq(pendingOp), eq("HASH_FAILED"), any(), anyLong());
    }

    @Test
    void hash_inputSizeMatchesDecodedLength() {
        byte[] raw = "exactly10b".getBytes();
        when(digestProvider.sha256Hex(any())).thenReturn("ff");
        String data = Base64.getEncoder().encodeToString(raw);

        HashResponse response = hashService.hash(new HashRequest(data));

        assertEquals(raw.length, response.inputSizeBytes());
    }
}
