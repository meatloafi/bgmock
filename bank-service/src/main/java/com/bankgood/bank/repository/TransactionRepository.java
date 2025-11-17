package com.bankgood.bank.repository;

import com.bankgood.bank.model.Transaction;
import com.bankgood.bank.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByFromAccountId(UUID fromAccountId);
}
