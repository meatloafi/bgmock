package com.bankgood.bank.controller;

import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/bank/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    // ===================== OUTGOING =====================
    @PostMapping("/outgoing")
    public ResponseEntity<?> createOutgoing(@RequestBody OutgoingTransactionEvent event) {
        return service.createOutgoingTransaction(event);
    }

    @GetMapping("/outgoing/{id}")
    public ResponseEntity<?> getOutgoing(@PathVariable UUID id) {
        return service.getOutgoingTransaction(id);
    }

    @DeleteMapping("/outgoing/{id}")
    public ResponseEntity<?> deleteOutgoing(@PathVariable UUID id) {
        return service.deleteOutgoingTransaction(id);
    }

    // ===================== INCOMING =====================

    @GetMapping("/incoming/{id}")
    public ResponseEntity<?> getIncoming(@PathVariable UUID id) {
        return service.getIncomingTransaction(id);
    }

    @DeleteMapping("/incoming/{id}")
    public ResponseEntity<?> deleteIncoming(@PathVariable UUID id) {
        return service.deleteIncomingTransaction(id);
    }

    // ===================== TRANSACTION EVENTS =====================
    @PostMapping("/incoming")
    public void handleIncomingEvent(@RequestBody IncomingTransactionEvent event) {
        service.handleIncomingTransaction(event);
    }

    @PostMapping("/outgoing/completed")
    public void handleCompletedEvent(@RequestBody TransactionResponseEvent event) {
        service.handleCompletedTransaction(event);
    }
}
