package com.bankgood.bank.service;

import com.bankgood.bank.event.TransactionEvent;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String OUTGOING_TOPIC = "transactions.outgoing";

    /**
     * Skapar en ny transaktion, drar pengar från avsändarens konto
     * och skickar ett TransactionEvent till Kafka.
     */
    @Transactional
    public TransactionEvent createTransaction(TransactionEvent request) {
        String fromAccountNumber = request.getFromAccountNumber();
        String toClearingNumber = request.getToBankgoodNumber();
        String toAccountNumber = request.getFromAccountNumber(); // du kan lägga till "toAccountNumber" i TransactionEvent senare
        BigDecimal amount = request.getAmount();

        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        // Dra pengarna
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        // Skapa transaktionen
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setFromAccountId(fromAccount.getAccountId());
        tx.setFromAccountNumber(fromAccountNumber);
        tx.setFromClearingNumber("000001"); // ta ev. från ENV TODO
        tx.setToBankgoodNumber(toClearingNumber);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        transactionRepository.save(tx);

        // Bygg Kafka-eventet
        TransactionEvent dto = new TransactionEvent(
                tx.getTransactionId(),
                tx.getFromAccountId(),
                tx.getFromClearingNumber(),
                tx.getFromAccountNumber(),
                tx.getToBankgoodNumber(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );

        // Skicka event till Kafka
        kafkaTemplate.send(OUTGOING_TOPIC, dto)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ TransactionEvent skickad till Kafka: {}", dto);
                    } else {
                        log.error("❌ Kunde inte skicka TransactionEvent", ex);
                    }
                });


        return dto;
    }

    /**
     * Hanterar inkommande svar på transaktion (TransactionResponseEvent)
     * och uppdaterar status i databasen.
     */
    @Transactional
    public void handleTransactionResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findById(response.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if ("FAILED".equalsIgnoreCase(response.getStatus().toString())) {
            // Kompenserande rollback
            Account fromAccount = accountRepository.findByAccountNumber(tx.getFromAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));
            fromAccount.setBalance(fromAccount.getBalance().add(tx.getAmount()));
            accountRepository.save(fromAccount);
        }

        tx.setStatus("SUCCESS".equalsIgnoreCase(response.getStatus().toString()) ?
                TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} uppdaterad med status: {}", tx.getTransactionId(), tx.getStatus());
    }
}
