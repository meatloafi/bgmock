package com.bankgood.bank.service;

import com.bankgood.bank.model.Account;
import com.bankgood.bank.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public Account createAccount(Account account) {
        account.setBalance(account.getBalance() == null ? BigDecimal.ZERO : account.getBalance());
        return accountRepository.save(account);
    }

    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Transactional
    public Account updateAccount(Account account) {
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(UUID accountId) {
        accountRepository.deleteById(accountId);
    }
}
