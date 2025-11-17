package com.bankgood.bank.controller;

import com.bankgood.bank.event.TransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.Transaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionEvent> createTransaction(@Valid @RequestBody TransactionEvent transactionEvent) {
        return ResponseEntity.accepted().body(transactionService.createTransaction(transactionEvent));
    }

    @PostMapping("/response")
    public ResponseEntity<Void> handleTransactionResponse(@Valid @RequestBody TransactionResponseEvent transactionResponseEvent) {
        transactionService.handleTransactionResponse(transactionResponseEvent);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Transaction>> getTransactionsByStatus(@PathVariable TransactionStatus status) {
        return ResponseEntity.ok(transactionService.getTransactionsByStatus(status));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactionsByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountId));
    }
}
