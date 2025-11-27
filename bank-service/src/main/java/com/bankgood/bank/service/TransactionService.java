package com.bankgood.bank.service;

import com.bankgood.bank.event.AccountDTO;
import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.IncomingTransaction;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.repository.IncomingTransactionRepository;
import com.bankgood.bank.repository.OutgoingTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    @Value("${BANK_CLEARING_NUMBER}")
    private String fromClearingNumber;

    private static final String TOPIC_INITIATED = "transactions.initiated";
    private static final String TOPIC_PROCESSED = "transactions.processed";

    private final OutgoingTransactionRepository outgoingRepo;
    private final IncomingTransactionRepository incomingRepo;
    AccountService accountService;

    private final KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> processedTemplate;

    public TransactionService(OutgoingTransactionRepository outgoingRepo,
                              IncomingTransactionRepository incomingRepo,
                              AccountService accountService,
                              KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate,
                              KafkaTemplate<String, TransactionResponseEvent> processedTemplate) {
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.accountService = accountService;
        this.initiatedTemplate = initiatedTemplate;
        this.processedTemplate = processedTemplate; }

    // ===================== OUTGOING =====================
    @Transactional
    public ResponseEntity<?> createOutgoingTransaction(OutgoingTransactionEvent event) {
        try {
            // 1. Reservera pengar via AccountService
            accountService.reserveFunds(event.getFromAccountId(), event.getAmount());

            // 2. Skapa transaktion
            OutgoingTransaction transaction = new OutgoingTransaction(
                    event.getFromAccountId(),
                    fromClearingNumber,
                    event.getFromAccountNumber(),
                    event.getToBankgoodNumber(),
                    event.getAmount()
            );
            OutgoingTransaction saved = outgoingRepo.save(transaction);

            event.setTransactionId(saved.getTransactionId());
            event.setStatus(saved.getStatus());
            event.setCreatedAt(saved.getCreatedAt());
            event.setUpdatedAt(saved.getUpdatedAt());
            event.setFromClearingNumber(fromClearingNumber);

            sendOutgoingTransaction(event); // Kafka
            log.info("INITIATED: " + event);

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

    @Transactional
    public ResponseEntity<?> deleteOutgoingTransaction(UUID id) {
        if (!outgoingRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Outgoing transaction not found");
        }
        outgoingRepo.deleteById(id);
        return ResponseEntity.ok("Outgoing transaction deleted successfully");
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

    @Transactional
    public ResponseEntity<?> deleteIncomingTransaction(UUID id) {
        if (!incomingRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Incoming transaction not found");
        }
        incomingRepo.deleteById(id);
        return ResponseEntity.ok("Incoming transaction deleted successfully");
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
                event.getTransactionId(),
                event.getToClearingNumber(),
                event.getToAccountNumber(),
                event.getAmount(),
                event.getStatus(),
                event.getCreatedAt(),
                LocalDateTime.now()
        );
        incomingRepo.save(transaction);

        try {
            // Spara incoming transaktionen
            incomingRepo.save(transaction);

            // Hämta konto och gör deposit
            AccountDTO toAccount = accountService.getAccount(event.getToAccountNumber());
            accountService.deposit(toAccount.getAccountId(), event.getAmount());

            // Lyckad transaktion
            transaction.setStatus(TransactionStatus.SUCCESS);
            incomingRepo.save(transaction);

            sendProcessedResponse(new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.SUCCESS,
                    "Transaction processed"
            ));

        } catch (Exception e) {
            log.error("Failed to process incoming transaction", e);

            // Sätt transaktionen som FAILED
            transaction.setStatus(TransactionStatus.FAILED);
            incomingRepo.save(transaction);

            // Skicka FAILED-response till clearing
            sendProcessedResponse(new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Transaction failed: " + e.getMessage()
            ));
        }
    }

    public void sendProcessedResponse(TransactionResponseEvent event) {
        log.info("Producing TransactionResponseEvent → transactions.processed");
        processedTemplate.send(TOPIC_PROCESSED, event);
    }

    // ===================== OUTGOING RESPONSE: CONSUME completed =====================

    @Transactional
    public void handleCompletedTransaction(TransactionResponseEvent event) {
        outgoingRepo.findByTransactionId(event.getTransactionId()).ifPresent(tx -> {
            tx.setStatus(event.getStatus());
            outgoingRepo.save(tx);

            try {
                if (event.getStatus() == TransactionStatus.SUCCESS) {
                    // Commita reserverade pengar
                    accountService.commitReservedFunds(tx.getFromAccountId(), tx.getAmount());
                } else {
                    // Släpp reserverade pengar
                    accountService.releaseReservedFunds(tx.getFromAccountId(), tx.getAmount());
                }
            } catch (Exception e) {
                log.error("Failed to update account balances for transaction {}: {}", tx.getTransactionId(), e.getMessage());
                // Eventuellt sätta transaktionen som FAILED om den inte redan är det
                if (tx.getStatus() != TransactionStatus.FAILED) {
                    tx.setStatus(TransactionStatus.FAILED);
                    outgoingRepo.save(tx);
                }
            }
        });
    }
}
