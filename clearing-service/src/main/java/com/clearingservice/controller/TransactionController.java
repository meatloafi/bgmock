package com.clearingservice.controller;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clearing")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    // Initierar en betalning
    @PostMapping("/transfer")
    public ResponseEntity<Void> initiateTransfer(@RequestBody TransactionEvent transactionEvent) {
        service.processIncomingTransaction(transactionEvent);
        return ResponseEntity.ok().build();
    }
}
