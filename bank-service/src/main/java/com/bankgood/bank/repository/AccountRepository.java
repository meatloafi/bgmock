package com.bankgood.bank.repository;

import com.bankgood.bank.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);

    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance WHERE a.accountId = :accountId")
    void updateBalance(UUID accountId, BigDecimal balance);

}
