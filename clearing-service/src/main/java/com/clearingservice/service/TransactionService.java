package com.clearingservice.service;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.Transaction;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service som hanterar hela clearing-logiken:
 * 1. Tar emot TransactionEvent från Bank A
 * 2. Lookup bankgiro → clearing + konto
 * 3. Sparar transaktionen som PENDING eller FAILED
 * 4. Forwardar till Bank B
 * 5. Tar emot TransactionResponseEvent från Bank B
 * 6. Uppdaterar transaktionen och skickar tillbaka status till Bank A
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
     * Hanterar inkommande TransactionEvent från Bank A
     */
    @Transactional
    public void processIncomingTransaction(TransactionEvent dto) {
        log.info("📥 Mottagen TransactionEvent från Bank A: {}", dto.getTransactionId());

        // Lookup mottagare via bankgiro
        BankMapping mapping = bankMappingRepository.findByBankgoodNumber(dto.getToClearingNumber())
                .orElse(null);

        // Skapa transaktion i DB
        Transaction tx = new Transaction();
        tx.setTransactionId(dto.getTransactionId());
        tx.setFromAccountNumber(dto.getFromAccountNumber());
        tx.setFromClearingNumber(dto.getFromClearingNumber());
        tx.setAmount(dto.getAmount());
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        if (mapping == null) {
            // Mottagare saknas → FAILED
            log.warn("❌ Mottagare saknas för bankgiro {}", dto.getToClearingNumber());
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason("Recipient not found");
            transactionRepository.save(tx);

            // Skicka direkt fail-response till Bank A
            TransactionResponseEvent response = new TransactionResponseEvent(
                    dto.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Recipient not found"
            );
            sendTransactionResponse(response);
            return;
        }

        // Mottagare finns → fyll i clearing + kontonummer
        tx.setToClearingNumber(mapping.getClearingNumber());
        tx.setToAccountNumber(mapping.getAccountNumber());
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        // Forward till Bank B
        TransactionEvent forwardEvent = toEvent(tx);
        forwardTransactionToBank(forwardEvent, mapping.getBankName());
    }

    /**
     * Forwardar TransactionEvent till mottagarbank (Bank B)
     */
    public void forwardTransactionToBank(TransactionEvent event, String bankName) {
        String bankTopic = "transactions.incoming." + bankName;

        transactionKafkaTemplate.send(bankTopic, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ TransactionEvent {} skickad till Bank B-topic {}", event.getTransactionId(), bankTopic);
                    } else {
                        log.error("❌ Kunde inte skicka TransactionEvent {} till Bank B-topic {}", event.getTransactionId(), bankTopic, ex);

                        // Markera transaktionen som FAILED
                        transactionRepository.findByTransactionId(event.getTransactionId()).ifPresent(tx -> {
                            tx.setStatus(TransactionStatus.FAILED);
                            tx.setFailureReason("Failed to forward transaction");
                            tx.setUpdatedAt(LocalDateTime.now());
                            transactionRepository.save(tx);

                            // Skicka fail-response till Bank A
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
     * Hanterar svar från Bank B (SUCCESS / FAILED)
     */
    @Transactional
    public void handleBankResponse(TransactionResponseEvent response) {
        Transaction tx = transactionRepository.findByTransactionId(response.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + response.getTransactionId()));

        // Uppdatera status i DB
        tx.setStatus(response.getStatus());
        tx.setFailureReason(response.getMessage());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} uppdaterad med status {}", tx.getTransactionId(), tx.getStatus());

        // Skicka tillbaka status till Bank A
        sendTransactionResponse(response);
    }

    /**
     * Skickar TransactionResponseEvent till Bank A
     */
    public void sendTransactionResponse(TransactionResponseEvent response) {
        responseKafkaTemplate.send(RESPONSE_TOPIC, response)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionResponseEvent skickad till Bank A: {}", response);
                    else log.error("❌ Kunde inte skicka TransactionResponseEvent {}", response, ex);
                });
    }

    /**
     * Konverterar Transaction → TransactionEvent för forwarding
     */
    private TransactionEvent toEvent(Transaction tx) {
        return new TransactionEvent(
                tx.getTransactionId(),
                null, // fromAccountId används ej i clearing
                tx.getFromClearingNumber(),
                tx.getFromAccountNumber(),
                tx.getToClearingNumber(),
                tx.getToAccountNumber(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}
