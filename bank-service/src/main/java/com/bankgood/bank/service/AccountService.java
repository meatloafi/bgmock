package com.bankgood.bank.service;

import com.bankgood.common.event.AccountDTO;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    // =================== DTO MAPPERS ===================
    public AccountDTO toDTO(Account account) {
        return new AccountDTO(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountHolder(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public Account toEntity(AccountDTO dto) {
        Account account = new Account();
        account.setAccountId(dto.getAccountId());
        account.setAccountNumber(dto.getAccountNumber());
        account.setAccountHolder(dto.getAccountHolder());
        account.setBalance(dto.getBalance());
        return account;
    }

    public List<AccountDTO> toDTOList(List<Account> accounts) {
        return accounts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // =================== CRUD ===================
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        Account account = toEntity(dto);
        account.setBalance(account.getBalance() == null ? BigDecimal.ZERO : account.getBalance());
        Account saved = accountRepository.save(account);
        return toDTO(saved);
    }

    public AccountDTO getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return toDTO(account);
    }

    public List<AccountDTO> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        return toDTOList(accounts);
    }

    @Transactional
    public AccountDTO updateAccount(UUID accountId, AccountDTO dto) {
        Account existing = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (dto.getBalance() == null || dto.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Balance cannot be null or negative");
        }

        existing.setAccountHolder(dto.getAccountHolder());
        existing.setAccountNumber(dto.getAccountNumber());
        existing.setBalance(dto.getBalance());
        Account updated = accountRepository.save(existing);
        return toDTO(updated);
    }

    @Transactional
    public void deleteAccount(UUID accountId) {
        Account existing = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        accountRepository.delete(existing);
    }

    @Transactional
    public AccountDTO adjustBalance(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        accountRepository.updateBalance(accountId, newBalance);
        account.setBalance(newBalance);
        return toDTO(account);
    }
}
