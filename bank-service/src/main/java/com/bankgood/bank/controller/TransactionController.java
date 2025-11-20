package com.bankgood.bank.controller;

import com.bankgood.common.event.OutgoingTransactionEvent;
import com.bankgood.common.event.IncomingTransactionEvent;
import com.bankgood.bank.model.IncomingTransaction;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
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

    @GetMapping("/outgoing")
    public ResponseEntity<List<OutgoingTransaction>> getAllOutgoing() {
        return service.getAllOutgoingTransactions();
    }

    @PutMapping("/outgoing/{id}")
    public ResponseEntity<?> updateOutgoing(@PathVariable UUID id, @RequestBody OutgoingTransactionEvent event) {
        return service.updateOutgoingTransaction(id, event);
    }

    @DeleteMapping("/outgoing/{id}")
    public ResponseEntity<?> deleteOutgoing(@PathVariable UUID id) {
        return service.deleteOutgoingTransaction(id);
    }

    // ===================== INCOMING =====================
    @PostMapping("/incoming")
    public ResponseEntity<?> createIncoming(@RequestBody IncomingTransactionEvent event) {
        return service.createIncomingTransaction(event);
    }

    @GetMapping("/incoming/{id}")
    public ResponseEntity<?> getIncoming(@PathVariable UUID id) {
        return service.getIncomingTransaction(id);
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<IncomingTransaction>> getAllIncoming() {
        return service.getAllIncomingTransactions();
    }

    @PutMapping("/incoming/{id}")
    public ResponseEntity<?> updateIncoming(@PathVariable UUID id, @RequestBody IncomingTransactionEvent event) {
        return service.updateIncomingTransaction(id, event);
    }

    @DeleteMapping("/incoming/{id}")
    public ResponseEntity<?> deleteIncoming(@PathVariable UUID id) {
        return service.deleteIncomingTransaction(id);
    }

    // ===================== ACCOUNT =====================

    @GetMapping("/outgoing/account/{accountId}")
    public ResponseEntity<?> getOutgoingByAccount(@PathVariable UUID accountId) {
        return service.getOutgoingTransactionsByAccount(accountId);
    }

    @GetMapping("/incoming/account/{accountId}")
    public ResponseEntity<?> getIncomingByAccount(@PathVariable UUID accountId) {
        return service.getIncomingTransactionsByAccount(accountId);
    }
}
