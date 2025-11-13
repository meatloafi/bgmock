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
     * Hanterar inkommande transaktion från Bank A.
     */
    @Transactional
    public void processIncomingTransaction(TransactionEvent dto) {
        log.info("📥 Mottagen TransactionEvent från Bank A: {}", dto);

        BankMapping mapping = bankMappingRepository.findByBankgoodNumber(dto.getToClearingNumber())
                .orElse(null);

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

            TransactionResponseEvent response = new TransactionResponseEvent(
                    dto.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Recipient not found"
            );
            sendTransactionResponse(response);
            return;
        }

        // Mottagare finns → fyll i clearing + account
        tx.setToClearingNumber(mapping.getClearingNumber());
        tx.setToAccountNumber(mapping.getAccountNumber());
        tx.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(tx);

        // Skicka vidare till mottagarbank (Bank B)
        String bankTopic = "transactions.incoming." + mapping.getBankName();
        TransactionEvent forwardEvent = toEvent(tx);

        transactionKafkaTemplate.send(bankTopic, forwardEvent)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ TransactionEvent {} skickad till Bank B-topic {}", tx.getTransactionId(), bankTopic);
                    } else {
                        log.error("❌ Kunde inte skicka TransactionEvent {} till Bank B-topic {}", tx.getTransactionId(), bankTopic, ex);
                        tx.setStatus(TransactionStatus.FAILED);
                        tx.setFailureReason("Failed to forward transaction");
                        transactionRepository.save(tx);

                        TransactionResponseEvent failResponse = new TransactionResponseEvent(
                                tx.getTransactionId(),
                                TransactionStatus.FAILED,
                                "Failed to forward transaction"
                        );
                        sendTransactionResponse(failResponse);
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

        tx.setStatus(response.getStatus());
        tx.setFailureReason(response.getMessage());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("📩 Transaction {} uppdaterad med status {}", tx.getTransactionId(), tx.getStatus());

        // Skicka tillbaka till Bank A
        sendTransactionResponse(response);
    }

    /**
     * Hjälpmetod för att skicka TransactionResponseEvent till Bank A
     */
    private void sendTransactionResponse(TransactionResponseEvent response) {
        responseKafkaTemplate.send(RESPONSE_TOPIC, response)
                .whenComplete((result, ex) -> {
                    if (ex == null) log.info("✅ TransactionResponseEvent skickad till Bank A: {}", response);
                    else log.error("❌ Kunde inte skicka TransactionResponseEvent {}", response, ex);
                });
    }

    /**
     * Konvertera Transaction → TransactionEvent för forwarding
     */
    private TransactionEvent toEvent(Transaction tx) {
        return new TransactionEvent(
                tx.getTransactionId(),
                null, // fromAccountId används inte i clearing
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
