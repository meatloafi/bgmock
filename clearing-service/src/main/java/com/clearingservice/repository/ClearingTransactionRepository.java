package com.clearingservice.repository;

import com.clearingservice.model.ClearingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClearingTransactionRepository extends JpaRepository<ClearingTransaction, UUID> {
    Optional<ClearingTransaction> findByTransactionId(UUID transactionId);
}
