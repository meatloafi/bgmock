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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * CONSUMER: transactions.initiated
     * Bank A skickar TransactionOutgoingEvent → Clearing-service
     */
    @Transactional
    public void handleOutgoingTransaction(OutgoingTransactionEvent event) {
        log.info("Clearing-service received OutgoingTransactionEvent for bankgiro {}", event.getToBankgoodNumber());

        Optional<OutgoingTransaction> existing = outgoingRepo.findById(event.getTransactionId());
        if (existing.isPresent()) {
            log.info("Transaction {} already exists, skipping", event.getTransactionId());
            return;
        }

        OutgoingTransaction outgoing = new OutgoingTransaction(
                event.getTransactionId(),
                event.getFromAccountId(),
                event.getFromClearingNumber(),
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt());
        try {
            outgoingRepo.save(outgoing);
        } catch (DataIntegrityViolationException e) {
            log.info("Transaction {} already processed", event.getTransactionId());
            return;
        }

        Optional<BankMapping> mappingOpt = mappingRepo.findByBankgoodNumber(event.getToBankgoodNumber());

        try {
            if (mappingOpt.isEmpty()) {
                TransactionResponseEvent failedResponse = new TransactionResponseEvent();
                failedResponse.setTransactionId(event.getTransactionId());
                failedResponse.setStatus(TransactionStatus.FAILED);
                failedResponse.setMessage("No bank-mapping found for " + event.getToBankgoodNumber());

                String payload = objectMapper.writeValueAsString(failedResponse);
                OutboxEvent outboxEvent = new OutboxEvent(
                        event.getTransactionId(),
                        TOPIC_COMPLETED,
                        event.getFromClearingNumber(),
                        payload);
                outboxEventRepo.save(outboxEvent);
                log.info("INITIATED -> COMPLETED: " + failedResponse);

            } else {
                BankMapping mapping = mappingOpt.get();
                IncomingTransactionEvent incomingEvent = new IncomingTransactionEvent(
                        event.getTransactionId(),
                        mapping.getClearingNumber(),
                        mapping.getAccountNumber(),
                        event.getAmount(),
                        TransactionStatus.PENDING,
                        event.getCreatedAt(),
                        LocalDateTime.now());

                String payload = objectMapper.writeValueAsString(incomingEvent);
                OutboxEvent outboxEvent = new OutboxEvent(
                        event.getTransactionId(),
                        TOPIC_FORWARDED,
                        mapping.getClearingNumber(),
                        payload);
                outboxEventRepo.save(outboxEvent);

                log.info("INITIATED -> FORWARDED: " + incomingEvent.toString());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON for transaction {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process transaction", e);
        }
    }

    /**
     * CONSUMER: transactions.processed
     * Bank B skickar response → Clearing-service
     */
    @Transactional
    public void handleProcessedTransaction(TransactionResponseEvent event) {

        Optional<OutgoingTransaction> existing = outgoingRepo.findById(event.getTransactionId());
        if (!existing.isPresent()) {
            return;
        }

        OutgoingTransaction transaction = existing.get();
        transaction.setStatus(event.getStatus());
        outgoingRepo.save(transaction);

        try {
            // Check for duplicate transcationId/topic combination
            boolean alreadyExists = outboxEventRepo.existsByTransactionIdAndTopic(
                    event.getTransactionId(), TOPIC_COMPLETED);

            if (!alreadyExists) {
                String payload = objectMapper.writeValueAsString(event);
                OutboxEvent outboxEvent = new OutboxEvent(
                        event.getTransactionId(),
                        TOPIC_COMPLETED,
                        transaction.getFromClearingNumber(),
                        payload);
                outboxEventRepo.save(outboxEvent);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON for transaction {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process transaction", e);
        }
    }

    public ResponseEntity<?> getOutgoingTransactionById(UUID transactionId) {
        return outgoingRepo.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
