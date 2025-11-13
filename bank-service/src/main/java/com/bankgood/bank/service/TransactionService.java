package com.bankgood.bank.service;

import com.bankgood.bank.event.TransactionEvent;
import com.bankgood.bank.config.BankConfig;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.Account;
import com.bankgood.bank.model.Transaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.repository.AccountRepository;
import com.bankgood.bank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BankConfig bankConfig;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String OUTGOING_TOPIC = "transactions.outgoing";
    /**
     * Creates a new transaction, withdraws money from the sender's account,
     * and sends a TransactionEvent to Kafka.
     */
    @Transactional
    public TransactionEvent createTransaction(TransactionEvent request) {
        String fromAccountNumber = request.getFromAccountNumber();
        String toClearingNumber = request.getToBankgoodNumber();
        // You can add a "toAccountNumber" to the TransactionEvent later
        String toAccountNumber = request.getFromAccountNumber();
        BigDecimal amount = request.getAmount();

        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        // Withdraw the money
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        // Create the transaction entity
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setFromAccountId(fromAccount.getId());
        tx.setFromAccountNumber(fromAccountNumber);
        tx.setFromClearingNumber(bankConfig.getClearingNumber()); 
        tx.setToBankgoodNumber(toClearingNumber);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        Transaction savedTx = transactionRepository.save(tx);

        // Build the Kafka event (DTO)
        TransactionEvent event = new TransactionEvent(
                savedTx.getTransactionId(),
                savedTx.getFromAccountId() != null ? savedTx.getFromAccountId().toString() : null,
                savedTx.getFromClearingNumber(),
                savedTx.getFromAccountNumber(),
                savedTx.getToBankgoodNumber(),
                savedTx.getAmount(),
                savedTx.getStatus(),
                savedTx.getCreatedAt(),
                savedTx.getUpdatedAt()
        );

        // Send event to Kafka
        kafkaTemplate.send(OUTGOING_TOPIC, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ TransactionEvent sent to Kafka: {}", event);
                    } else {
                        log.error("❌ Failed to send TransactionEvent", ex);
                    }
                });

        return event;
    }

    /**
     * Handles an incoming transaction response (TransactionResponseEvent)
     * and updates the status in the database.
     */
    @Transactional
    public void handleTransactionResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findById(UUID.fromString(response.getTransactionId()))
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if ("FAILED".equalsIgnoreCase(response.getStatus())) {
            // Compensating rollback
            Account fromAccount = accountRepository.findByAccountNumber(tx.getFromAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));
            fromAccount.setBalance(fromAccount.getBalance().add(tx.getAmount()));
            accountRepository.save(fromAccount);
        }

        tx.setStatus("SUCCESS".equalsIgnoreCase(response.getStatus()) ?
                TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} updated with status: {}", tx.getTransactionId(), tx.getStatus());
    }
}
