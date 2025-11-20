package com.clearingservice.repository;

import com.clearingservice.model.OutgoingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<OutgoingTransaction, UUID> {
    Optional<OutgoingTransaction> findByTransactionId(UUID transactionId);
}
