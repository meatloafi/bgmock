package com.bankgood.bank.repository;

import com.bankgood.bank.model.IncomingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IncomingTransactionRepository extends JpaRepository<IncomingTransaction, UUID> {
}
