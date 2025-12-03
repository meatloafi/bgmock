package com.bankgood.bank.service;

import com.bankgood.bank.event.AccountDTO;
import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.ReserveFundsResult;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final OutboxEventRepository outboxEventRepo;

    private final AccountService accountService;

    public TransactionService(
            ObjectMapper objectMapper,
            OutgoingTransactionRepository outgoingRepo,
            IncomingTransactionRepository incomingRepo,
            AccountService accountService,
            OutboxEventRepository outboxEventRepo) {
        this.objectMapper = objectMapper;
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.accountService = accountService;
        this.outboxEventRepo = outboxEventRepo;
    }

    // ======== CRUD ========
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

    // ====== Business logic ======
    /**
     * Producer: transactions.initiated
     * Bank initiates transaction to clearing service
     */
    @Transactional
    public void createOutgoingTransaction(OutgoingTransactionEvent event) {
        OutgoingTransaction transaction = new OutgoingTransaction(
                fromClearingNumber,
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount());

        // Check amount > 0
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            failTransaction(transaction, event, "Amount must be above 0");
            return;
        }

        ReserveFundsResult result = accountService.reserveFunds(event.getFromAccountNumber(), event.getAmount());

        // Check reserve funds result
        if (!result.isSuccess()) {
            failTransaction(transaction, event, result.getMessage());
            return;
        }

        // Funds are available -> create normal transaction
        transaction.setStatus(TransactionStatus.PENDING);
        OutgoingTransaction saved = outgoingRepo.save(transaction);

        event.setTransactionId(saved.getTransactionId());
        event.setStatus(saved.getStatus());
        event.setCreatedAt(saved.getCreatedAt());
        event.setUpdatedAt(saved.getUpdatedAt());
        event.setFromClearingNumber(fromClearingNumber);

        saveOutboxEvent(event.getTransactionId(), TOPIC_INITIATED, event);
        log.info("Initiated transaction with ID: {}", event.getTransactionId());
    }



    /**
     * CONSUMER: transactions.forwarded
     * Bank sends response to Clearing-service
     */
    @Transactional
    public void handleIncomingTransaction(IncomingTransactionEvent event) {
        log.info("Received transaction with ID: {}", event.getTransactionId());

        IncomingTransaction transaction = new IncomingTransaction(
                event.getTransactionId(),
                event.getToClearingNumber(),
                event.getToAccountNumber(),
                event.getAmount(),
                event.getStatus());

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
        boolean alreadyExists = outboxEventRepo.existsByTransactionIdAndTopic(event.getTransactionId(),
                TOPIC_PROCESSED);
        if (!alreadyExists) {
            TransactionResponseEvent response = new TransactionResponseEvent(event.getTransactionId(), finalStatus,
                    message);
            saveOutboxEvent(event.getTransactionId(), TOPIC_PROCESSED, response);
        }
        log.info("Processed transaction with ID: {}", event.getTransactionId());
    }


    /**
     * CONSUMER: transactions.completed
     * Bank commits or releases funds
     */
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

    // ====== Helpers ======

        private void failTransaction(OutgoingTransaction transaction, OutgoingTransactionEvent event, String reason) {
        log.info("Cannot create transaction: {}", reason);
        transaction.setStatus(TransactionStatus.FAILED);
        // optional: transaction.setFailureReason(reason);
        OutgoingTransaction saved = outgoingRepo.save(transaction);
        event.setTransactionId(saved.getTransactionId());
        event.setStatus(saved.getStatus());
    }

    private void saveOutboxEvent(UUID transactionId, String topic, Object eventPayload) {
        try {
            String payload = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = new OutboxEvent(transactionId, topic, fromClearingNumber, payload);
            outboxEventRepo.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON for transaction {}", transactionId, e);
            throw new RuntimeException("Failed to process outbox event", e);
        }
    }
}
