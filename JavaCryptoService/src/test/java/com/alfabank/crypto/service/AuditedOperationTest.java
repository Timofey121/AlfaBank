package com.alfabank.crypto.service;

import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.model.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditedOperationTest {

    @Mock private AuditService auditService;

    private AuditedOperation auditedOperation;
    private CryptoOperation pendingOp;

    @BeforeEach
    void setUp() {
        auditedOperation = new AuditedOperation(auditService);
        pendingOp = new CryptoOperation();
        pendingOp.setId("op-id");
        pendingOp.setType("HASH");
        when(auditService.createPending(eq("HASH"), any(), any())).thenReturn(pendingOp);
    }

    @Test
    void run_onSuccess_createsPendingMarksSuccessAndReturnsActionValue() {
        String result = auditedOperation.run(OperationType.HASH, null, "alias", "TEST_FAILED",
                (msg, cause) -> new CryptoOperationException(msg, cause),
                op -> AuditedOperation.Outcome.of("hello", "hello".getBytes()));

        assertEquals("hello", result);
        verify(auditService).createPending("HASH", null, "alias");
        verify(auditService).markSuccess(eq(pendingOp), eq("hello".getBytes()), anyLong());
        verify(auditService, never()).markFailed(any(), any(), any(), anyLong());
    }

    @Test
    void run_whenActionThrowsRuntimeException_marksFailedAndRethrowsUnchanged() {
        RuntimeException boom = new IllegalStateException("boom");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                auditedOperation.run(OperationType.HASH, null, null, "TEST_FAILED",
                        (msg, cause) -> new CryptoOperationException(msg, cause),
                        op -> { throw boom; }));

        assertSame(boom, thrown);
        verify(auditService).markFailed(eq(pendingOp), eq("TEST_FAILED"), eq("boom"), anyLong());
        verify(auditService, never()).markSuccess(any(), any(), anyLong());
    }

    @Test
    void run_whenActionThrowsCheckedException_wrapsUsingProvidedFactory() {
        Exception checked = new GeneralSecurityException("checked failure");

        CryptoOperationException thrown = assertThrows(CryptoOperationException.class, () ->
                auditedOperation.run(OperationType.HASH, null, null, "TEST_FAILED",
                        (msg, cause) -> new CryptoOperationException("wrapped: " + msg, cause),
                        op -> { throw checked; }));

        assertEquals("wrapped: checked failure", thrown.getMessage());
        assertSame(checked, thrown.getCause());
        verify(auditService).markFailed(eq(pendingOp), eq("TEST_FAILED"), eq("checked failure"), anyLong());
    }

    @Test
    void run_passesCreatedOperationIntoAction() {
        auditedOperation.run(OperationType.HASH, null, null, "TEST_FAILED",
                (msg, cause) -> new CryptoOperationException(msg, cause),
                op -> {
                    assertSame(pendingOp, op);
                    return AuditedOperation.Outcome.of(op.getId());
                });
    }
}
