package com.bankgood.bank.repository;

import com.bankgood.bank.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    
}