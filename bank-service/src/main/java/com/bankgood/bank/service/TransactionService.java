package com.bankgood.bank.service;

import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.bank.model.Account;
import com.bankgood.bank.model.Transaction;
import com.bankgood.common.model.TransactionStatus;
import com.bankgood.bank.repository.AccountRepository;
import com.bankgood.bank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    @Value("${BANK_CLEARING_NUMBER}")
    private String fromClearingNumber;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    private final KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate;

    private static final String OUTGOING_TOPIC = "transactions.outgoing";
    private static final String RESPONSE_TOPIC = "transactions.response";

    // ----------------- Create outgoing transaction (Bank A → Clearing) -----------------
    @Transactional
    public TransactionEvent createTransaction(TransactionEvent request) {
        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender account not found"));

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        // Debit the sender
        BigDecimal newBalance = fromAccount.getBalance().subtract(request.getAmount());
        accountRepository.updateBalance(fromAccount.getAccountId(), newBalance);
        fromAccount.setBalance(newBalance);

        // Create transaction
        Transaction tx = new Transaction();
        tx.setFromAccountId(fromAccount.getAccountId());
        tx.setFromAccountNumber(request.getFromAccountNumber());
        tx.setFromClearingNumber(fromClearingNumber);
        tx.setToBankgoodNumber(request.getToBankgoodNumber());
        tx.setAmount(request.getAmount());
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        tx = transactionRepository.save(tx); // Save and get the generated transactionId

        // Send TransactionEvent to clearing-service
        TransactionEvent event = toEvent(tx);
        sendTransactionEvent(event);

        return event;
    }

    // ----------------- Handle incoming TransactionEvent (Bank B receives) -----------------
    @Transactional
    public void handleIncomingTransaction(TransactionEvent event) {
        log.info("📥 Handling incoming TransactionEvent: {}", event.getTransactionId());

        Account toAccount = accountRepository.findByAccountNumber(event.getToAccountNumber())
                .orElse(null);

        TransactionResponseEvent response;
        if (toAccount == null) {
            // Account not found → reject transaction
            response = new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Receiver account not found"
            );
            log.warn("❌ Account {} not found, sending failed response", event.getToAccountNumber());
        } else {
            // Account found → credit recipient
            BigDecimal newBalance = toAccount.getBalance().add(event.getAmount());
            accountRepository.updateBalance(toAccount.getAccountId(), newBalance);
            toAccount.setBalance(newBalance);

            // Create local Transaction for tracking
            Transaction tx = new Transaction();
            tx.setTransactionId(event.getTransactionId());
            tx.setFromAccountId(event.getFromAccountId());
            tx.setFromAccountNumber(event.getFromAccountNumber());
            tx.setFromClearingNumber(event.getFromClearingNumber());
            tx.setToAccountNumber(event.getToAccountNumber());
            tx.setAmount(event.getAmount());
            tx.setStatus(TransactionStatus.SUCCESS);
            tx.setCreatedAt(LocalDateTime.now());
            tx.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(tx);

            response = new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.SUCCESS,
                    "Transaction successful"
            );
            log.info("✅ Transaction {} completed, sending success response", event.getTransactionId());
        }

        // Send response to clearing-service
        sendTransactionResponse(response);
    }

    // ----------------- Handle incoming TransactionResponseEvent (Bank A receives response from clearing) -----------------
    @Transactional
    public void handleTransactionResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findById(response.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (response.getStatus() == TransactionStatus.FAILED) {
            // Compensating rollback
            Account fromAccount = accountRepository.findByAccountNumber(tx.getFromAccountNumber())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender account not found"));

            BigDecimal refundedBalance = fromAccount.getBalance().add(tx.getAmount());
            accountRepository.updateBalance(fromAccount.getAccountId(), refundedBalance);
            fromAccount.setBalance(refundedBalance);
        }

        tx.setStatus(response.getStatus());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} updated with status: {}", tx.getTransactionId(), tx.getStatus());
    }

    // ----------------- Helper methods -----------------
    private void sendTransactionEvent(TransactionEvent event) {
        transactionKafkaTemplate.send(OUTGOING_TOPIC, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionEvent sent: {}", event);
                    else log.error("❌ Failed to send TransactionEvent", ex);
                });
    }

    private void sendTransactionResponse(TransactionResponseEvent response) {
        responseKafkaTemplate.send(RESPONSE_TOPIC, response)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionResponseEvent sent: {}", response);
                    else log.error("❌ Failed to send TransactionResponseEvent", ex);
                });
    }

    private TransactionEvent toEvent(Transaction tx) {
        return new TransactionEvent(
                tx.getTransactionId(),
                tx.getFromAccountId(),
                tx.getFromClearingNumber(),
                tx.getFromAccountNumber(),
                tx.getToBankgoodNumber(),
                tx.getToClearingNumber(), // Added toClearingNumber
                tx.getToAccountNumber(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }

    // ----------------- Get transactions -----------------
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public List<Transaction> getTransactionsByStatus(TransactionStatus status) {
        return transactionRepository.findByStatus(status);
    }

    public List<Transaction> getTransactionsByAccount(UUID accountId) {
        return transactionRepository.findByFromAccountId(accountId);
    }
}
