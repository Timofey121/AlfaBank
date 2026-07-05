package com.alfabank.crypto.repository;

import com.alfabank.crypto.model.CryptoOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoOperationRepository extends JpaRepository<CryptoOperation, String> {
    Page<CryptoOperation> findByType(String type, Pageable pageable);
}
