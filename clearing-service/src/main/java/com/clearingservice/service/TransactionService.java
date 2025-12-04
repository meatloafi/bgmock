package com.clearingservice.service;

import com.clearingservice.event.IncomingTransactionEvent;
import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.OutboxEvent;
import com.clearingservice.model.OutgoingTransaction;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.OutboxEventRepository;
import com.clearingservice.repository.OutgoingTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    private static final String TOPIC_FORWARDED = "transactions.forwarded";
    private static final String TOPIC_COMPLETED = "transactions.completed";

    private final ObjectMapper objectMapper;
    private final OutgoingTransactionRepository outgoingRepo;
    private final BankMappingRepository mappingRepo;
    private final OutboxEventRepository outboxEventRepo;

    public TransactionService(
            ObjectMapper objectMapper,
            OutgoingTransactionRepository outgoingRepo,
            BankMappingRepository mappingRepo,
            OutboxEventRepository outboxEventRepo) {
        this.objectMapper = objectMapper;
        this.outgoingRepo = outgoingRepo;
        this.mappingRepo = mappingRepo;
        this.outboxEventRepo = outboxEventRepo;
    }

    @Transactional
    public void handleOutgoingTransaction(OutgoingTransactionEvent event) {
        log.info("Processing outgoing transaction {}", event.getTransactionId());

        // Idempotency check
        if (outgoingRepo.existsById(event.getTransactionId())) {
            log.info("Transaction {} already processed, skipping", event.getTransactionId());
            return;
        }

        // Lookup bank mapping first (before committing to anything)
        Optional<BankMapping> mappingOpt = mappingRepo.findByBankgoodNumber(event.getToBankgoodNumber());

        if (mappingOpt.isEmpty()) {
            handleFailedRoute(event);
            return;
        }

        handleSuccessfulRoute(event, mappingOpt.get());
    }

    @Transactional
    private void handleFailedRoute(OutgoingTransactionEvent event) {
        log.warn("No bank-mapping found for {}", event.getToBankgoodNumber());

        // Save the outgoing transaction with FAILED status
        OutgoingTransaction outgoing = new OutgoingTransaction(
                event.getTransactionId(),
                event.getFromClearingNumber(),
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount(),
                TransactionStatus.FAILED,
                event.getCreatedAt(),
                event.getUpdatedAt());
        outgoingRepo.save(outgoing);

        // Send failure response back to originating bank
        TransactionResponseEvent failedResponse = new TransactionResponseEvent();
        failedResponse.setTransactionId(event.getTransactionId());
        failedResponse.setStatus(TransactionStatus.FAILED);
        failedResponse.setMessage("No bank-mapping found for " + event.getToBankgoodNumber());

        saveOutboxEvent(
                event.getTransactionId(),
                TOPIC_COMPLETED,
                failedResponse,
                event.getFromClearingNumber());

        log.info("Rejected transaction {} - no routing available", event.getTransactionId());
    }

    @Transactional
    private void handleSuccessfulRoute(OutgoingTransactionEvent event, BankMapping mapping) {
        log.info("Routing transaction {} to bank {}", event.getTransactionId(), mapping.getClearingNumber());

        // Save the outgoing transaction with PENDING status
        OutgoingTransaction outgoing = new OutgoingTransaction(
                event.getTransactionId(),
                event.getFromClearingNumber(),
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount(),
                TransactionStatus.PENDING,
                event.getCreatedAt(),
                event.getUpdatedAt());
        outgoingRepo.save(outgoing);

        // Create incoming event for destination bank
        IncomingTransactionEvent incomingEvent = new IncomingTransactionEvent();
        incomingEvent.setTransactionId(event.getTransactionId());
        incomingEvent.setToClearingNumber(mapping.getClearingNumber());
        incomingEvent.setToAccountNumber(mapping.getAccountNumber());
        incomingEvent.setAmount(event.getAmount());
        incomingEvent.setStatus(TransactionStatus.PENDING);
        incomingEvent.setCreatedAt(event.getCreatedAt());

        saveOutboxEvent(
                event.getTransactionId(),
                TOPIC_FORWARDED,
                incomingEvent,
                mapping.getClearingNumber());

        log.info("Forwarded transaction {}", event.getTransactionId());
    }

    /**
     * CONSUMER: transactions.processed
     * Bank B skickar response → Clearing-service
     */
    @Transactional
    public void handleProcessedTransaction(TransactionResponseEvent event) {

        // Idempotency check
        Optional<OutgoingTransaction> existing = outgoingRepo.findById(event.getTransactionId());
        if (!existing.isPresent()) {
            return;
        }

        OutgoingTransaction transaction = existing.get();
        transaction.setStatus(event.getStatus());
        outgoingRepo.save(transaction);

        saveOutboxEvent(event.getTransactionId(), TOPIC_COMPLETED, event, transaction.getFromClearingNumber());
        // try {

        // String payload = objectMapper.writeValueAsString(event);
        // OutboxEvent outboxEvent = new OutboxEvent(
        // event.getTransactionId(),
        // TOPIC_COMPLETED,
        // transaction.getFromClearingNumber(),
        // payload);
        // outboxEventRepo.save(outboxEvent);
        // log.info("Sent response for ID: {}", event.getTransactionId());

        // } catch (JsonProcessingException e) {
        // log.error("Failed to serialize event to JSON for transaction {}",
        // event.getTransactionId(), e);
        // throw new RuntimeException("Failed to process transaction", e);
        // }
    }

    public ResponseEntity<?> getOutgoingTransactionById(UUID transactionId) {
        return outgoingRepo.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private void saveOutboxEvent(UUID transactionId, String topic, Object eventPayload, String fromClearingNumber) {
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
