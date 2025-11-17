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

    // ----------------- Skapa egen transaktion (Bank A → Clearing) -----------------
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

        // Skicka TransactionEvent till clearing-service
        TransactionEvent event = toEvent(tx);
        sendTransactionEvent(event);

        return event;
    }

    // ----------------- Hantera inkommande TransactionEvent (Bank B tar emot) -----------------
    @Transactional
    public void handleIncomingTransaction(TransactionEvent event) {
        log.info("📥 Hanterar inkommande TransactionEvent: {}", event.getTransactionId());

        Account toAccount = accountRepository.findByAccountNumber(event.getToAccountNumber())
                .orElse(null);

        TransactionResponseEvent response;
        if (toAccount == null) {
            // Konto finns inte → avvisa transaktion
            response = new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Receiver account not found"
            );
            log.warn("❌ Konto {} saknas, skickar failed response", event.getToAccountNumber());
        } else {
            // Konto finns → kreditera mottagare
            BigDecimal newBalance = toAccount.getBalance().add(event.getAmount());
            accountRepository.updateBalance(toAccount.getAccountId(), newBalance);
            toAccount.setBalance(newBalance);

            // Skapa lokalt Transaction för spårning
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
            log.info("✅ Transaction {} genomförd, skickar success response", event.getTransactionId());
        }

        // Skicka response till clearing-service
        sendTransactionResponse(response);
    }

    // ----------------- Hantera inkommande TransactionResponseEvent (Bank A får svar från clearing) -----------------
    @Transactional
    public void handleTransactionResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findById(response.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (response.getStatus() == TransactionStatus.FAILED) {
            // Kompenserande rollback
            Account fromAccount = accountRepository.findByAccountNumber(tx.getFromAccountNumber())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender account not found"));

            BigDecimal refundedBalance = fromAccount.getBalance().add(tx.getAmount());
            accountRepository.updateBalance(fromAccount.getAccountId(), refundedBalance);
            fromAccount.setBalance(refundedBalance);
        }

        tx.setStatus(response.getStatus());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} uppdaterad med status: {}", tx.getTransactionId(), tx.getStatus());
    }

    // ----------------- Hjälpmetoder -----------------
    private void sendTransactionEvent(TransactionEvent event) {
        transactionKafkaTemplate.send(OUTGOING_TOPIC, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionEvent skickad: {}", event);
                    else log.error("❌ Kunde inte skicka TransactionEvent", ex);
                });
    }

    private void sendTransactionResponse(TransactionResponseEvent response) {
        responseKafkaTemplate.send(RESPONSE_TOPIC, response)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionResponseEvent skickad: {}", response);
                    else log.error("❌ Kunde inte skicka TransactionResponseEvent", ex);
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

    // ----------------- Hämta transaktioner -----------------
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
