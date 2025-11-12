package com.bankgood.bank.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bankgood.bank.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

}
