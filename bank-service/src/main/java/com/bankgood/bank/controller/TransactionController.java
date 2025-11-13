package com.bankgood.bank.controller;

import com.bankgood.bank.event.TransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionEvent> createTransaction(@RequestBody TransactionEvent transactionEvent) {
        return ResponseEntity.ok(transactionService.createTransaction(transactionEvent));
    }

    @PostMapping("/response")
    public ResponseEntity<Void> handleTransactionResponse(@RequestBody TransactionResponseEvent transactionResponseEvent) {
        transactionService.handleTransactionResponse(transactionResponseEvent);
        return ResponseEntity.ok().build();
    }
}
