package com.bankgood.bank.service;

import com.bankgood.bank.model.Account;
import com.bankgood.bank.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public Account updateAccount(Account account) {
        Account existing = getAccount(account.getAccountId());

        if (account.getBalance() == null || account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Balance cannot be null or negative");
        }

        existing.setAccountHolder(account.getAccountHolder());
        existing.setAccountNumber(account.getAccountNumber());
        existing.setBalance(account.getBalance());
        return accountRepository.save(existing);
    }

    @Transactional
    public void deleteAccount(UUID accountId) {
        Account existing = getAccount(accountId);
        accountRepository.delete(existing);
    }

    /**
     * Adjust balance with a positive or negative amount.
     * Throws exception if resulting balance would be negative.
     */
    @Transactional
    public void adjustBalance(UUID accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        // use repository-level update for efficiency
        accountRepository.updateBalance(accountId, newBalance);
        account.setBalance(newBalance);
    }
}
