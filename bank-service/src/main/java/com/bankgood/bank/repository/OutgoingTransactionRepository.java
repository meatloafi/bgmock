package com.bankgood.bank.repository;

import com.bankgood.bank.model.OutgoingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutgoingTransactionRepository extends JpaRepository<OutgoingTransaction, UUID> {
    Optional<OutgoingTransaction> findByTransactionId(UUID transactionId);
}
