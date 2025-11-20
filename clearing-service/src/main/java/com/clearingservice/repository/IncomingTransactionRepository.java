package com.clearingservice.repository;

import com.clearingservice.model.IncomingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncomingTransactionRepository extends JpaRepository<IncomingTransaction, UUID> {
    Optional<IncomingTransaction> findByTransactionId(UUID transactionId);
}
