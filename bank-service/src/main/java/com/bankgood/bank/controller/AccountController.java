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

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccount(@PathVariable UUID id) {
        log.info("API CALL: Get account with ID {}", id);
        AccountDTO result = accountService.getAccount(id);
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
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountDTO dto) {
        log.info("API CALL: Update account {} with data {}", id, dto);
        AccountDTO result = accountService.updateAccount(id, dto);
        log.info("API RESULT: Updated account {}", result);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        log.info("API CALL: Delete account {}", id);
        accountService.deleteAccount(id);
        log.info("API RESULT: Deleted account {}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountDTO> deposit(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Deposit {} to account {}", amount, id);
        AccountDTO result = accountService.deposit(id, amount);
        log.info("API RESULT: After deposit: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<AccountDTO> reserveFunds(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Reserve {} on account {}", amount, id);
        AccountDTO result = accountService.reserveFunds(id, amount);
        log.info("API RESULT: After reserve: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/commit")
    public ResponseEntity<AccountDTO> commitReservedFunds(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Commit {} reserved funds on account {}", amount, id);
        AccountDTO result = accountService.commitReservedFunds(id, amount);
        log.info("API RESULT: After commit: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<AccountDTO> releaseReservedFunds(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {
        log.info("API CALL: Release {} reserved funds on account {}", amount, id);
        AccountDTO result = accountService.releaseReservedFunds(id, amount);
        log.info("API RESULT: After release: {}", result);
        return ResponseEntity.ok(result);
    }
}
