package com.bankgood.bank.service;

import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.Account;
import com.bankgood.bank.model.IncomingTransaction;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.repository.AccountRepository;
import com.bankgood.bank.repository.IncomingTransactionRepository;
import com.bankgood.bank.repository.OutgoingTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {

    @Value("${BANK_CLEARING_NUMBER}")
    private String fromClearingNumber;

    private static final String TOPIC_INITIATED = "transactions.initiated";
    private static final String TOPIC_PROCESSED = "transactions.processed";

    private final OutgoingTransactionRepository outgoingRepo;
    private final IncomingTransactionRepository incomingRepo;
    private final AccountRepository accountRepo;

    private final KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> processedTemplate;

    public TransactionService(OutgoingTransactionRepository outgoingRepo,
                              IncomingTransactionRepository incomingRepo,
                              AccountRepository accountRepo,
                              KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate,
                              KafkaTemplate<String, TransactionResponseEvent> processedTemplate) {
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.accountRepo = accountRepo;
        this.initiatedTemplate = initiatedTemplate;
        this.processedTemplate = processedTemplate; }

    // ===================== OUTGOING =====================
    @Transactional
    public ResponseEntity<?> createOutgoingTransaction(OutgoingTransactionEvent event) {
        try {
            OutgoingTransaction transaction = new OutgoingTransaction(
                    event.getFromAccountId(),
                    event.getFromClearingNumber(),
                    event.getFromAccountNumber(),
                    event.getToBankgoodNumber(),
                    event.getAmount()
            );

            OutgoingTransaction saved = outgoingRepo.save(transaction);

            event.setTransactionId(saved.getTransactionId());
            event.setStatus(saved.getStatus());
            event.setCreatedAt(saved.getCreatedAt());
            event.setUpdatedAt(saved.getUpdatedAt());

            sendOutgoingTransaction(event); // PRODUCE till Kafka
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Failed to create outgoing transaction", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create outgoing transaction");
        }
    }

    public ResponseEntity<?> getOutgoingTransaction(UUID id) {
        return outgoingRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public ResponseEntity<List<OutgoingTransaction>> getAllOutgoingTransactions() {
        return ResponseEntity.ok(outgoingRepo.findAll());
    }

    @Transactional
    public ResponseEntity<?> updateOutgoingTransaction(UUID id, OutgoingTransactionEvent event) {
        Optional<OutgoingTransaction> opt = outgoingRepo.findById(id);
        if (opt.isPresent()) {
            OutgoingTransaction transaction = opt.get();
            transaction.setFromAccountId(event.getFromAccountId());
            transaction.setFromClearingNumber(event.getFromClearingNumber());
            transaction.setFromAccountNumber(event.getFromAccountNumber());
            transaction.setToBankgoodNumber(event.getToBankgoodNumber());
            transaction.setAmount(event.getAmount());
            transaction.setStatus(event.getStatus());
            return ResponseEntity.ok(outgoingRepo.save(transaction));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Outgoing transaction not found");
    }

    @Transactional
    public ResponseEntity<?> deleteOutgoingTransaction(UUID id) {
        if (!outgoingRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Outgoing transaction not found");
        }
        outgoingRepo.deleteById(id);
        return ResponseEntity.ok("Outgoing transaction deleted successfully");
    }

    public ResponseEntity<?> getOutgoingTransactionsByAccount(UUID accountId) {
        if (!accountRepo.existsById(accountId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
        List<OutgoingTransaction> transactions = outgoingRepo.findAll()
                .stream()
                .filter(tx -> tx.getFromAccountId().equals(accountId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(transactions);
    }

    // ===================== INCOMING =====================
    @Transactional
    public ResponseEntity<?> createIncomingTransaction(IncomingTransactionEvent event) {
        try {
            IncomingTransaction transaction = new IncomingTransaction(
                    event.getToClearingNumber(),
                    event.getToAccountNumber(),
                    event.getAmount()
            );
            transaction.setStatus(event.getStatus());
            IncomingTransaction saved = incomingRepo.save(transaction);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Failed to create incoming transaction", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create incoming transaction");
        }
    }

    public ResponseEntity<?> getIncomingTransaction(UUID id) {
        return incomingRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public ResponseEntity<List<IncomingTransaction>> getAllIncomingTransactions() {
        return ResponseEntity.ok(incomingRepo.findAll());
    }

    @Transactional
    public ResponseEntity<?> updateIncomingTransaction(UUID id, IncomingTransactionEvent event) {
        Optional<IncomingTransaction> opt = incomingRepo.findById(id);
        if (opt.isPresent()) {
            IncomingTransaction transaction = opt.get();
            transaction.setToClearingNumber(event.getToClearingNumber());
            transaction.setToAccountNumber(event.getToAccountNumber());
            transaction.setAmount(event.getAmount());
            transaction.setStatus(event.getStatus());
            return ResponseEntity.ok(incomingRepo.save(transaction));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Incoming transaction not found");
    }

    @Transactional
    public ResponseEntity<?> deleteIncomingTransaction(UUID id) {
        if (!incomingRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Incoming transaction not found");
        }
        incomingRepo.deleteById(id);
        return ResponseEntity.ok("Incoming transaction deleted successfully");
    }

    // ===================== ACCOUNT =====================

    public ResponseEntity<?> getIncomingTransactionsByAccount(UUID accountId) {
        Optional<Account> accountOpt = accountRepo.findById(accountId);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        String accountNumber = accountOpt.get().getAccountNumber();

        List<IncomingTransaction> transactions = incomingRepo.findAll()
                .stream()
                .filter(tx -> tx.getToAccountNumber().equals(accountNumber))
                .collect(Collectors.toList());

        return ResponseEntity.ok(transactions);
    }

    // ===================== OUTGOING: PRODUCE initiated =====================

    public void sendOutgoingTransaction(OutgoingTransactionEvent event) {
        log.info("Producing OutgoingTransactionEvent → transactions.initiated");
        initiatedTemplate.send(TOPIC_INITIATED, event);
    }

    // ===================== INCOMING: CONSUME forwarded =====================

    @Transactional
    public void handleIncomingTransaction(IncomingTransactionEvent event) {
        log.info("Received IncomingTransactionEvent for account {}", event.getToAccountNumber());

        // 1. Spara incoming transaktionen
        IncomingTransaction transaction = new IncomingTransaction(
                event.getToClearingNumber(),
                event.getToAccountNumber(),
                event.getAmount()
        );
        incomingRepo.save(transaction);

        // 2. Kontrollera om transaktionen kan genomföras
        boolean success = true; // TODO: saldo-kontroll

        TransactionResponseEvent response = new TransactionResponseEvent(
                event.getTransactionId(),
                success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED, // Sätt rätt status
                success ? "Transaction processed" : "Insufficient funds" // Sätt rätt message
        );


        // 3. Skicka response tillbaka till clearing → transactions.processed
        sendProcessedResponse(response);
    }

    public void sendProcessedResponse(TransactionResponseEvent event) {
        log.info("Producing TransactionResponseEvent → transactions.processed");
        processedTemplate.send(TOPIC_PROCESSED, event);
    }

    // ===================== OUTGOING RESPONSE: CONSUME completed =====================

    @Transactional
    public void handleCompletedTransaction(TransactionResponseEvent event) {
        log.info("Received completed TransactionResponseEvent for {}", event.getTransactionId());

        outgoingRepo.findByTransactionId(event.getTransactionId()).ifPresent(tx -> {
            tx.setStatus(event.getStatus());
            outgoingRepo.save(tx);
        });
    }
}
