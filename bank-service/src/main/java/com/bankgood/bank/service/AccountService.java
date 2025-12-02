package com.bankgood.bank.service;

import com.bankgood.bank.event.AccountDTO;
import com.bankgood.bank.event.ReserveFundsResult;
import com.bankgood.bank.model.Account;
import com.bankgood.bank.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                account.getReservedBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    public Account toEntity(AccountDTO dto) {
        Account account = new Account();
        account.setAccountId(dto.getAccountId());
        account.setAccountNumber(dto.getAccountNumber());
        account.setAccountHolder(dto.getAccountHolder());

        account.setBalance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO);
        account.setReservedBalance(dto.getReservedBalance() != null ? dto.getReservedBalance() : BigDecimal.ZERO);
        account.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
        account.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

        return account;
    }

    public List<AccountDTO> toDTOList(List<Account> accounts) {
        return accounts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // =================== CRUD ===================
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        Account account = toEntity(dto);
        Account saved = accountRepository.save(account);
        return toDTO(saved);
    }

    public AccountDTO getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return toDTO(account);
    }

    public AccountDTO getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return toDTO(account);
    }

    public List<AccountDTO> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        return toDTOList(accounts);
    }

    @Transactional
    public AccountDTO updateAccount(String accountNumber, AccountDTO dto) {
        Account existing = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (dto.getBalance() == null || dto.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Balance cannot be null or negative");
        }

        existing.setAccountHolder(dto.getAccountHolder());
        existing.setAccountNumber(dto.getAccountNumber());
        Account updated = accountRepository.save(existing);
        return toDTO(updated);
    }

    @Transactional
    public void deleteAccount(String accountNumber) {
        Account existing = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        accountRepository.delete(existing);
    }

    // =================== TRANSACTION LOGIC ===================

    /**
     * Deposits a positive amount into the specified account.
     *
     * @param accountId the UUID of the account
     * @param amount    the amount to deposit (must be positive)
     * @return the updated AccountDTO
     * @throws ResponseStatusException if the account is not found or the amount is
     *                                 non-positive
     */
    @Transactional
    public AccountDTO deposit(String accountNumber, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit amount must be positive");

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        account.setBalance(account.getBalance().add(amount));
        return toDTO(account);
    }

    /**
     * Reserves a specified amount from the available balance of the account.
     * The reserved amount is locked for a pending transaction.
     *
     * @param accountNumber the account number
     * @param amount    the amount to reserve (must be positive and <= available
     *                  balance)
     * @return the updated AccountDTO
     * @throws ResponseStatusException if the account is not found, the amount is
     *                                 non-positive, or insufficient funds
     */
@Transactional
public ReserveFundsResult reserveFunds(String accountNumber, BigDecimal amount) {
    Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
    if (account == null) {
        return new ReserveFundsResult(false, "Account not found", null);
    }

    BigDecimal available = account.getBalance().subtract(account.getReservedBalance());
    if (available.compareTo(amount) < 0) {
        return new ReserveFundsResult(false,
            "Insufficient funds: requested " + amount + ", available " + available,
            toDTO(account));
    }

    account.setReservedBalance(account.getReservedBalance().add(amount));
    return new ReserveFundsResult(true, "Funds reserved", toDTO(account));
}

    /**
     * Commits a previously reserved amount, deducting it from the total balance.
     * Should be called when the transaction is confirmed/settled.
     *
     * @param accountId the UUID of the account
     * @param amount    the amount to commit (must be positive and <= reserved
     *                  balance)
     * @return the updated AccountDTO
     * @throws ResponseStatusException if the account is not found, the amount is
     *                                 non-positive, or insufficient reserved funds
     */
    @Transactional
    public AccountDTO commitReservedFunds(String accountNumber, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (account.getReservedBalance().compareTo(amount) < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough reserved funds");

        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        account.setBalance(account.getBalance().subtract(amount));

        return toDTO(account);
    }

    /**
     * Releases a previously reserved amount back to the available balance.
     * Should be called when the transaction is cancelled or fails.
     *
     * @param accountId the UUID of the account
     * @param amount    the amount to release (must be positive and <= reserved
     *                  balance)
     * @return the updated AccountDTO
     * @throws ResponseStatusException if the account is not found, the amount is
     *                                 non-positive, or insufficient reserved funds
     */
    @Transactional
    public AccountDTO releaseReservedFunds(String accountNumber, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (account.getReservedBalance().compareTo(amount) < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough reserved funds to release");

        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        
        return toDTO(account);
    }

}
