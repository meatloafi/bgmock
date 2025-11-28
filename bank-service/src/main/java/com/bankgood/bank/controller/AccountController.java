package com.bankgood.bank.controller;

import com.bankgood.bank.event.AccountDTO;
import com.bankgood.bank.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/bank/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody AccountDTO dto) {
        log.info("API CALL: Create account {}", dto);
        AccountDTO result = accountService.createAccount(dto);
        log.info("API RESULT: Created account {}", result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountDTO> getAccountByNumber(@PathVariable String accountNumber) {
        log.info("API CALL: Get account with account number {}", accountNumber);
        AccountDTO result = accountService.getAccountByNumber(accountNumber);
        log.info("API RESULT: Get account response {}", result);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        log.info("API CALL: Get all accounts");
        List<AccountDTO> result = accountService.getAllAccounts();
        log.info("API RESULT: Returned {} accounts", result.size());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable String accountNumber, @Valid @RequestBody AccountDTO dto) {
        log.info("API CALL: Update account {} with data {}", accountNumber, dto);
        AccountDTO result = accountService.updateAccount(accountNumber, dto);
        log.info("API RESULT: Updated account {}", result);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String accountNumber) {
        log.info("API CALL: Delete account {}", accountNumber);
        accountService.deleteAccount(accountNumber);
        log.info("API RESULT: Deleted account {}", accountNumber);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountDTO> deposit(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Deposit {} to account {}", amount, accountNumber);
        AccountDTO result = accountService.deposit(accountNumber, amount);
        log.info("API RESULT: After deposit: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<AccountDTO> reserveFunds(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Reserve {} on account {}", amount, accountNumber);
        AccountDTO result = accountService.reserveFunds(accountNumber, amount);
        log.info("API RESULT: After reserve: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/commit")
    public ResponseEntity<AccountDTO> commitReservedFunds(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Commit {} reserved funds on account {}", amount, accountNumber);
        AccountDTO result = accountService.commitReservedFunds(accountNumber, amount);
        log.info("API RESULT: After commit: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<AccountDTO> releaseReservedFunds(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Release {} reserved funds on account {}", amount, accountNumber);
        AccountDTO result = accountService.releaseReservedFunds(accountNumber, amount);
        log.info("API RESULT: After release: {}", result);
        return ResponseEntity.ok(result);
    }
}
