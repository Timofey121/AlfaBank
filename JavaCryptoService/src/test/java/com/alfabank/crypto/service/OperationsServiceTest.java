package com.alfabank.crypto.service;

import com.alfabank.crypto.dto.response.OperationDetailResponse;
import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.repository.CryptoOperationRepository;
import com.alfabank.crypto.repository.EncryptionDetailRepository;
import com.alfabank.crypto.repository.FetchDetailRepository;
import com.alfabank.crypto.repository.SignatureDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsServiceTest {

    @Mock private CryptoOperationRepository repository;
    @Mock private SignatureDetailRepository signatureDetailRepository;
    @Mock private EncryptionDetailRepository encryptionDetailRepository;
    @Mock private FetchDetailRepository fetchDetailRepository;

    private OperationsService operationsService;

    @BeforeEach
    void setUp() {
        operationsService = new OperationsService(
                repository, signatureDetailRepository, encryptionDetailRepository, fetchDetailRepository);
    }

    @Test
    void list_rejectsInvalidType() {
        assertThrows(IllegalArgumentException.class, () -> operationsService.list(0, 20, "BOGUS"));
    }

    @Test
    void list_capsPageSizeAt200() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        operationsService.list(0, 5000, null);

        verify(repository).findAll(argThat((PageRequest pr) -> pr.getPageSize() == 200));
    }

    @Test
    void list_filtersByTypeWhenProvided() {
        when(repository.findByType(eq("HASH"), any())).thenReturn(new PageImpl<>(List.of()));

        operationsService.list(0, 20, "HASH");

        verify(repository).findByType(eq("HASH"), any());
        verify(repository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void getById_returnsEmpty_whenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(operationsService.getById("missing").isEmpty());
    }

    @Test
    void getById_returnsDetailResponse_whenFound() {
        CryptoOperation op = new CryptoOperation();
        op.setId("op-1");
        op.setType("HASH");
        op.setStatus("SUCCESS");
        op.setCreatedAt(Instant.now());
        when(repository.findById("op-1")).thenReturn(Optional.of(op));

        Optional<OperationDetailResponse> result = operationsService.getById("op-1");

        assertTrue(result.isPresent());
        assertEquals("op-1", result.get().id());
        verifyNoInteractions(signatureDetailRepository, encryptionDetailRepository, fetchDetailRepository);
    }

    @Test
    void getById_looksUpEncryptionDetail_forEncryptType() {
        CryptoOperation op = new CryptoOperation();
        op.setId("op-2");
        op.setType("ENCRYPT");
        op.setStatus("SUCCESS");
        op.setCreatedAt(Instant.now());
        when(repository.findById("op-2")).thenReturn(Optional.of(op));
        when(encryptionDetailRepository.findById("op-2")).thenReturn(Optional.empty());

        operationsService.getById("op-2");

        verify(encryptionDetailRepository).findById("op-2");
        verifyNoInteractions(signatureDetailRepository, fetchDetailRepository);
    }
}
