package com.clearingservice.service;

import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.Transaction;
import com.bankgood.common.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service that handles the complete clearing logic:
 * 1. Receives TransactionEvent from Bank A
 * 2. Looks up bankgood number → clearing number + account number
 * 3. Saves the transaction as PENDING or FAILED
 * 4. Forwards to Bank B
 * 5. Receives TransactionResponseEvent from Bank B
 * 6. Updates the transaction and sends status back to Bank A
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankMappingRepository bankMappingRepository;

    private final KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate;

    private static final String RESPONSE_TOPIC = "transactions.response";

    /**
     * Handles incoming TransactionEvent from Bank A
     */
    @Transactional
    public void processIncomingTransaction(TransactionEvent dto) {
        log.info("📥 Received TransactionEvent from Bank A: {}", dto.getTransactionId());

        // Look up recipient via bankgood number
        BankMapping mapping = bankMappingRepository.findByBankgoodNumber(dto.getToBankgoodNumber())
                .orElse(null);

        // Create transaction in DB
        Transaction tx = new Transaction();
        tx.setTransactionId(dto.getTransactionId());
        tx.setFromAccountNumber(dto.getFromAccountNumber());
        tx.setFromClearingNumber(dto.getFromClearingNumber());
        tx.setAmount(dto.getAmount());
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        if (mapping == null) {
            // Recipient not found → FAILED
            log.warn("❌ Recipient not found for bankgood number {}", dto.getToBankgoodNumber());
            tx.setStatus(TransactionStatus.FAILED);
            tx.setMessage("Recipient not found");
            transactionRepository.save(tx);

            // Send immediate failure response to Bank A
            TransactionResponseEvent response = new TransactionResponseEvent(
                    dto.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Recipient not found"
            );
            sendTransactionResponse(response);
            return;
        }

        // Recipient found → populate clearing number + account number
        tx.setToClearingNumber(mapping.getClearingNumber());
        tx.setToAccountNumber(mapping.getAccountNumber());
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        // Forward to Bank B
        TransactionEvent forwardEvent = toEvent(tx);
        forwardTransactionToBank(forwardEvent, mapping.getBankName());
    }

    /**
     * Forwards TransactionEvent to recipient bank (Bank B)
     */
    public void forwardTransactionToBank(TransactionEvent event, String bankName) {
        String bankTopic = "transactions.incoming." + bankName;

        transactionKafkaTemplate.send(bankTopic, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ TransactionEvent {} sent to Bank B topic: {}", event.getTransactionId(), bankTopic);
                    } else {
                        log.error("❌ Failed to send TransactionEvent {} to Bank B topic: {}", event.getTransactionId(), bankTopic, ex);

                        // Mark transaction as FAILED
                        transactionRepository.findByTransactionId(event.getTransactionId()).ifPresent(tx -> {
                            tx.setStatus(TransactionStatus.FAILED);
                            tx.setMessage("Failed to forward transaction");
                            tx.setUpdatedAt(LocalDateTime.now());
                            transactionRepository.save(tx);

                            // Send failure response to Bank A
                            TransactionResponseEvent failResponse = new TransactionResponseEvent(
                                    tx.getTransactionId(),
                                    TransactionStatus.FAILED,
                                    "Failed to forward transaction"
                            );
                            sendTransactionResponse(failResponse);
                        });
                    }
                });
    }

    /**
     * Handles response from Bank B (SUCCESS / FAILED)
     */
    @Transactional
    public void handleBankResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findByTransactionId(response.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + response.getTransactionId()));

        // Update status in DB
        tx.setStatus(response.getStatus());
        tx.setMessage(response.getMessage());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} updated with status: {}", tx.getTransactionId(), tx.getStatus());

        // Send status back to Bank A
        sendTransactionResponse(response);
    }

    /**
     * Sends TransactionResponseEvent to Bank A
     */
    public void sendTransactionResponse(TransactionResponseEvent response) {
        responseKafkaTemplate.send(RESPONSE_TOPIC, response)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionResponseEvent sent to Bank A: {}", response);
                    else log.error("❌ Failed to send TransactionResponseEvent: {}", response, ex);
                });
    }

    /**
     * Converts Transaction → TransactionEvent for forwarding
     */
    private TransactionEvent toEvent(Transaction tx) {
        return new TransactionEvent(
                tx.getTransactionId(),
                null, // fromAccountId not used in clearing
                tx.getFromClearingNumber(),
                tx.getFromAccountNumber(),
                null, // toBankgoodNumber is not available in clearing-service's Transaction model
                tx.getToClearingNumber(),
                tx.getToAccountNumber(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}
