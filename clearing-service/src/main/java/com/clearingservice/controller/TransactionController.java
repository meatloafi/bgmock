package com.clearingservice.controller;

import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/clearing/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ===================== OUTGOING TRANSACTIONS =====================
    @PostMapping("/outgoing")
    public ResponseEntity<?> createOutgoingTransaction(@RequestBody OutgoingTransactionEvent event) {
        try {
            transactionService.handleOutgoingTransaction(event);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Transaction accepted for processing",
                    "transactionId", event.getTransactionId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Endpoint för att simulera att en bank skickar processed-response (för test)
    @PostMapping("/processed")
    public ResponseEntity<?> handleProcessedTransaction(@RequestBody TransactionResponseEvent event) {
        return transactionService.handleProcessedTransaction(event);
    }

    // ===================== FETCH TRANSACTION =====================
    @GetMapping("/{transactionId}")
    public ResponseEntity<?> getOutgoingTransactionById(@PathVariable UUID transactionId) {
        return transactionService.getOutgoingTransactionById(transactionId);
    }
}
