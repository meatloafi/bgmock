package com.bankgood.bank.controller;

import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.model.IncomingTransaction;
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
    public ResponseEntity<OutgoingTransaction> createOutgoing(@RequestBody OutgoingTransactionEvent event) {
        return ResponseEntity.ok(service.createOutgoingTransaction(event));
    }

    @GetMapping("/outgoing/{id}")
    public ResponseEntity<OutgoingTransaction> getOutgoing(@PathVariable UUID id) {
        return service.getOutgoingTransaction(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/outgoing")
    public List<OutgoingTransaction> getAllOutgoing() {
        return service.getAllOutgoingTransactions();
    }

    @PutMapping("/outgoing/{id}")
    public ResponseEntity<OutgoingTransaction> updateOutgoing(@PathVariable UUID id,
                                                              @RequestBody OutgoingTransactionEvent event) {
        OutgoingTransaction updated = service.updateOutgoingTransaction(id, event);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/outgoing/{id}")
    public void deleteOutgoing(@PathVariable UUID id) {
        service.deleteOutgoingTransaction(id);
    }

    // ===================== INCOMING =====================
    @PostMapping("/incoming")
    public ResponseEntity<IncomingTransaction> createIncoming(@RequestBody IncomingTransactionEvent event) {
        return ResponseEntity.ok(service.createIncomingTransaction(event));
    }

    @GetMapping("/incoming/{id}")
    public ResponseEntity<IncomingTransaction> getIncoming(@PathVariable UUID id) {
        return service.getIncomingTransaction(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/incoming")
    public List<IncomingTransaction> getAllIncoming() {
        return service.getAllIncomingTransactions();
    }

    @PutMapping("/incoming/{id}")
    public ResponseEntity<IncomingTransaction> updateIncoming(@PathVariable UUID id,
                                                              @RequestBody IncomingTransactionEvent event) {
        IncomingTransaction updated = service.updateIncomingTransaction(id, event);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/incoming/{id}")
    public void deleteIncoming(@PathVariable UUID id) {
        service.deleteIncomingTransaction(id);
    }
}
