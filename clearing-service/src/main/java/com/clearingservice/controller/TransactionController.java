package com.clearingservice.controller;

import com.bankgood.common.event.OutgoingTransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/clearing/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ===================== OUTGOING TRANSACTIONS =====================

    @PostMapping("/outgoing")
    public ResponseEntity<?> createOutgoingTransaction(@RequestBody OutgoingTransactionEvent event) {
        return transactionService.handleOutgoingTransaction(event);
    }

    // Endpoint för att simulera att en bank skickar processed-response (för test)
    @PostMapping("/processed")
    public ResponseEntity<?> handleProcessedTransaction(@RequestBody TransactionResponseEvent event) {
        return transactionService.handleProcessedTransaction(event);
    }
}
