package com.alfabank.crypto.service;

import com.alfabank.crypto.dto.response.OperationDetailResponse;
import com.alfabank.crypto.dto.response.OperationRecordResponse;
import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.model.OperationType;
import com.alfabank.crypto.repository.CryptoOperationRepository;
import com.alfabank.crypto.repository.EncryptionDetailRepository;
import com.alfabank.crypto.repository.FetchDetailRepository;
import com.alfabank.crypto.repository.SignatureDetailRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class OperationsService {

    private static final int MAX_PAGE_SIZE = 200;

    private final CryptoOperationRepository repository;
    private final SignatureDetailRepository signatureDetailRepository;
    private final EncryptionDetailRepository encryptionDetailRepository;
    private final FetchDetailRepository fetchDetailRepository;

    public OperationsService(
            CryptoOperationRepository repository,
            SignatureDetailRepository signatureDetailRepository,
            EncryptionDetailRepository encryptionDetailRepository,
            FetchDetailRepository fetchDetailRepository) {
        this.repository = repository;
        this.signatureDetailRepository = signatureDetailRepository;
        this.encryptionDetailRepository = encryptionDetailRepository;
        this.fetchDetailRepository = fetchDetailRepository;
    }

    public Page<OperationRecordResponse> list(int page, int size, String type) {
        if (type != null && parseType(type) == null) {
            throw new IllegalArgumentException(
                    "Invalid operation type: " + type + ". Allowed values: " + Arrays.toString(OperationType.values()));
        }
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, cappedSize, Sort.by("createdAt").descending());
        Page<CryptoOperation> result = type != null
                ? repository.findByType(type, pageable)
                : repository.findAll(pageable);
        return result.map(OperationRecordResponse::from);
    }

    public Optional<OperationDetailResponse> getById(String id) {
        return repository.findById(id).map(this::toDetailResponse);
    }

    private OperationDetailResponse toDetailResponse(CryptoOperation op) {
        OperationDetailResponse.SignatureInfo signature = null;
        OperationDetailResponse.EncryptionInfo encryption = null;
        OperationDetailResponse.FetchInfo fetch = null;

        OperationType type = parseType(op.getType());
        if (type != null) {
            switch (type) {
                case SIGN, VERIFY -> signature = signatureDetailRepository.findById(op.getId())
                        .map(OperationDetailResponse.SignatureInfo::from)
                        .orElse(null);
                case ENCRYPT, DECRYPT -> encryption = encryptionDetailRepository.findById(op.getId())
                        .map(OperationDetailResponse.EncryptionInfo::from)
                        .orElse(null);
                case FETCH -> fetch = fetchDetailRepository.findById(op.getId())
                        .map(OperationDetailResponse.FetchInfo::from)
                        .orElse(null);
                default -> { }
            }
        }

        return OperationDetailResponse.from(op, signature, encryption, fetch);
    }

    private OperationType parseType(String raw) {
        try {
            return OperationType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
