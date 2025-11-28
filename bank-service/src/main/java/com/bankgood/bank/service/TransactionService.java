package com.bankgood.bank.service;

import com.bankgood.bank.event.AccountDTO;
import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.IncomingTransaction;
import com.bankgood.bank.model.OutboxEvent;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.repository.IncomingTransactionRepository;
import com.bankgood.bank.repository.OutboxEventRepository;
import com.bankgood.bank.repository.OutgoingTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final ObjectMapper objectMapper;
    private final OutgoingTransactionRepository outgoingRepo;
    private final IncomingTransactionRepository incomingRepo;
    private final AccountService accountService;
    private final OutboxEventRepository outboxEventRepo;

    private final KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> processedTemplate;

    public TransactionService(
            ObjectMapper objectMapper,
            OutgoingTransactionRepository outgoingRepo,
            IncomingTransactionRepository incomingRepo,
            AccountService accountService,
            OutboxEventRepository outboxEventRepo,
            KafkaTemplate<String, OutgoingTransactionEvent> initiatedTemplate,
            KafkaTemplate<String, TransactionResponseEvent> processedTemplate) {
        this.objectMapper = objectMapper;
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.accountService = accountService;
        this.outboxEventRepo = outboxEventRepo;
        this.initiatedTemplate = initiatedTemplate;
        this.processedTemplate = processedTemplate;
    }

    // ===================== OUTGOING =====================
    @Transactional
    public void createOutgoingTransaction(OutgoingTransactionEvent event) {
        // TODO: Idempotency check
        try {
            // 1. Reservera pengar via AccountService
            accountService.reserveFunds(event.getFromAccountNumber(), event.getAmount());

            // 2. Skapa transaktion
            OutgoingTransaction transaction = new OutgoingTransaction(
                    event.getFromAccountId(),
                    fromClearingNumber,
                    event.getFromAccountNumber(),
                    event.getToBankgoodNumber(),
                    event.getAmount());
            OutgoingTransaction saved = outgoingRepo.save(transaction);

            event.setTransactionId(saved.getTransactionId());
            event.setStatus(saved.getStatus());
            event.setCreatedAt(saved.getCreatedAt());
            event.setUpdatedAt(saved.getUpdatedAt());
            event.setFromClearingNumber(fromClearingNumber);

            sendOutgoingTransaction(event); // Kafka

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    event.getTransactionId(),
                    TOPIC_INITIATED,
                    fromClearingNumber,
                    payload);
            outboxEventRepo.save(outboxEvent);
            // sendOutgoingTransaction(event);
            log.info("Initiated transaction with ID: " + event.getTransactionId());

            log.info("INITIATED: " + event);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON for transaction {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process transaction", e);
        } catch (Exception e) {
            log.error("Failed to create outgoing transaction", e);
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
        initiatedTemplate.send(TOPIC_INITIATED, event);
    }

    /**
     * CONSUMER: transactions.forwarded
     * Bank sends response to Clearing-service
     */
    // @Transactional
    // public void handleIncomingTransaction(IncomingTransactionEvent event) {
    //     log.info("Received transaction with ID: ", event.getTransactionId());

    //     // 1. Spara incoming transaktionen
    //     IncomingTransaction transaction = new IncomingTransaction(
    //             event.getTransactionId(),
    //             event.getToClearingNumber(),
    //             event.getToAccountNumber(),
    //             event.getAmount(),
    //             event.getStatus(),
    //             event.getCreatedAt(),
    //             LocalDateTime.now());

    //     try {
    //         // Spara incoming transaktionen
    //         incomingRepo.save(transaction);

    //         // Hämta konto och gör deposit
    //         AccountDTO toAccount = accountService.getAccountByNumber(event.getToAccountNumber());
    //         accountService.deposit(toAccount.getAccountId(), event.getAmount());

    //         // Lyckad transaktion
    //         transaction.setStatus(TransactionStatus.SUCCESS);
    //         incomingRepo.save(transaction);

    //         TransactionResponseEvent response = new TransactionResponseEvent(
    //                 event.getTransactionId(),
    //                 TransactionStatus.SUCCESS,
    //                 "Transaction processed");

    //         boolean alreadyExists = outboxEventRepo.existsByTransactionIdAndTopic(
    //                 event.getTransactionId(), TOPIC_PROCESSED);

    //         if (!alreadyExists) {
    //             String payload = objectMapper.writeValueAsString(response);
    //             OutboxEvent outboxEvent = new OutboxEvent(
    //                     event.getTransactionId(),
    //                     TOPIC_PROCESSED,
    //                     fromClearingNumber,
    //                     payload);
    //             outboxEventRepo.save(outboxEvent);

    //             log.info("Processed transaction with ID: " + event.getTransactionId());
    //         }

    //     } catch (JsonProcessingException e) {
    //         log.error("Failed to serialize event to JSON for transaction {}", event.getTransactionId(), e);
    //         throw new RuntimeException("Failed to process transaction", e);
    //     } catch (Exception e) {
    //         log.error("Failed to process incoming transaction", e);

    //         // Sätt transaktionen som FAILED
    //         transaction.setStatus(TransactionStatus.FAILED);
    //         incomingRepo.save(transaction);
    //         TransactionResponseEvent response = new TransactionResponseEvent(
    //                 event.getTransactionId(),
    //                 TransactionStatus.FAILED,
    //                 "Transaction failed: " + e.getMessage());

    //         String payload = objectMapper.writeValueAsString(response);
    //         OutboxEvent outboxEvent = new OutboxEvent(
    //                 event.getTransactionId(),
    //                 TOPIC_PROCESSED,
    //                 fromClearingNumber,
    //                 payload);
    //         outboxEventRepo.save(outboxEvent);

    //         log.info("Processed transaction with ID: " + event.getTransactionId());
    //     }
    // }

    @Transactional
    public void handleIncomingTransaction(IncomingTransactionEvent event) {
        log.info("Received transaction with ID: {}", event.getTransactionId());

        IncomingTransaction transaction = new IncomingTransaction(
                event.getTransactionId(),
                event.getToClearingNumber(),
                event.getToAccountNumber(),
                event.getAmount(),
                event.getStatus(),
                event.getCreatedAt(),
                LocalDateTime.now());

        TransactionStatus finalStatus;
        String message;

        try {
            // Spara incoming transaktionen
            incomingRepo.save(transaction);

            // Hämta konto och gör deposit
            AccountDTO toAccount = accountService.getAccountByNumber(event.getToAccountNumber());
            accountService.deposit(toAccount.getAccountNumber(), event.getAmount());

            // Lyckad transaktion
            finalStatus = TransactionStatus.SUCCESS;
            message = "Transaction processed";
        } catch (Exception e) {
            log.error("Failed to process incoming transaction {}", event.getTransactionId(), e);

            finalStatus = TransactionStatus.FAILED;
            message = "Transaction failed: " + e.getMessage();
        }

        // Uppdatera transaction status
        transaction.setStatus(finalStatus);
        incomingRepo.save(transaction);

        // Skapa outbox event om det inte redan finns
        try {
            boolean alreadyExists = outboxEventRepo.existsByTransactionIdAndTopic(event.getTransactionId(),
                    TOPIC_PROCESSED);
            if (!alreadyExists) {
                TransactionResponseEvent response = new TransactionResponseEvent(event.getTransactionId(), finalStatus,
                        message);
                String payload = objectMapper.writeValueAsString(response);
                OutboxEvent outboxEvent = new OutboxEvent(event.getTransactionId(), TOPIC_PROCESSED, fromClearingNumber,
                        payload);
                outboxEventRepo.save(outboxEvent);
            }
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event to JSON for transaction {}", event.getTransactionId(), ex);
            throw new RuntimeException("Failed to process transaction outbox event", ex);
        }

        log.info("Processed transaction with ID: {}", event.getTransactionId());

    }

    public void sendProcessedResponse(TransactionResponseEvent event) {
        log.info("Producing TransactionResponseEvent → transactions.processed");
        processedTemplate.send(TOPIC_PROCESSED, event);
    }

    // ===================== OUTGOING RESPONSE: CONSUME completed
    // =====================

 @Transactional
public void handleCompletedTransaction(TransactionResponseEvent event) {
    OutgoingTransaction tx = outgoingRepo.findByTransactionId(event.getTransactionId())
            .orElse(null);

    if (tx == null) {
        log.warn("Transaction {} not found", event.getTransactionId());
        return;
    }

    tx.setStatus(event.getStatus());

    try {
        if (event.getStatus() == TransactionStatus.SUCCESS) {
            accountService.commitReservedFunds(tx.getFromAccountNumber(), tx.getAmount());
            log.info("Committed funds");
        } else {
            accountService.releaseReservedFunds(tx.getFromAccountNumber(), tx.getAmount());
            log.info("Released funds");
        }
    } catch (Exception e) {
        log.error("Failed to update account balances for transaction {}: {}", tx.getTransactionId(),
                e.getMessage());
        tx.setStatus(TransactionStatus.FAILED);
        throw new RuntimeException("Failed to update accounts", e); // force rollback
    }
}

}
